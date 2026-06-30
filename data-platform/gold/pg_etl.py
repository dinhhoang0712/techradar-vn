"""
Gold — PostgreSQL ETL
Đọc dữ liệu từ Neo4j (Knowledge Graph) → rebuild bảng tech_analytics.

Logic giống RadarAnalyticsEtlService.java nhưng chạy từ Data Platform (Python)
thay vì từ Spring Boot gateway.

Chạy: mỗi đêm lúc 3:00 AM (cấu hình trong scheduler).
"""
from __future__ import annotations

import psycopg2
from datetime import date
from collections import defaultdict
from loguru import logger
from neo4j import GraphDatabase

from common.db import get_pg_conn, get_neo4j_driver, log_pipeline_run
from config import Settings

_ARTICLE_Q = """
MATCH (t:Technology)<-[:MENTIONS]-(a:Article)
WHERE a.published_date IS NOT NULL
WITH t.name AS tech,
     substring(toString(a.published_date), 0, 7) AS ym
WHERE ym IS NOT NULL
RETURN tech, ym, count(*) AS cnt
"""

_JOB_Q = """
MATCH (t:Technology)<-[:REQUIRES]-(j:Job)
WITH t.name AS tech,
     substring(toString(coalesce(j.posted_date, j.due_date, j.created_at)), 0, 7) AS ym
WHERE ym IS NOT NULL
RETURN tech, ym, count(DISTINCT j) AS cnt
"""

_SNAPSHOT_Q = """
MATCH (t:Technology)<-[:REQUIRES]-(j:Job)
RETURN t.name AS tech, count(DISTINCT j) AS cnt
"""


def _parse_ym(ym_str: str) -> date | None:
    try:
        year, month = ym_str.split("-")
        return date(int(year), int(month), 1)
    except Exception:
        return None


def _growth(current: int, previous: int | None) -> float:
    if not previous:
        return 0.0
    return round(((current - previous) / previous) * 100, 2)


def run(settings: Settings) -> int:
    """
    Rebuild tech_analytics từ Neo4j.
    Returns số rows đã upsert.
    """
    logger.info("Gold PG ETL: starting...")

    pg_conn = get_pg_conn(settings)
    run_id = log_pipeline_run(pg_conn, "gold_pg_etl", "running")

    try:
        driver = get_neo4j_driver(settings)

        # {tech -> {ym -> [job_count, article_count]}}
        data: dict[str, dict[str, list[int]]] = defaultdict(lambda: defaultdict(lambda: [0, 0]))
        snapshot: dict[str, int] = {}

        with driver.session() as session:
            for rec in session.run(_ARTICLE_Q):
                tech, ym = rec["tech"], rec["ym"]
                if tech and ym:
                    data[tech][ym][1] += rec["cnt"]

            for rec in session.run(_JOB_Q):
                tech, ym = rec["tech"], rec["ym"]
                if tech and ym:
                    data[tech][ym][0] += rec["cnt"]

            for rec in session.run(_SNAPSHOT_Q):
                if rec["tech"]:
                    snapshot[rec["tech"]] = rec["cnt"]

        driver.close()

        # Inject snapshot vào tháng hiện tại
        from datetime import datetime
        current_ym = datetime.now().strftime("%Y-%m")
        for tech, cnt in snapshot.items():
            data[tech][current_ym][0] = max(data[tech][current_ym][0], cnt)

        # Tính ranking theo snapshot
        ranked = sorted(snapshot.items(), key=lambda x: x[1], reverse=True)
        rank_map = {tech: i + 1 for i, (tech, _) in enumerate(ranked)}

        rows_upserted = 0
        with pg_conn.cursor() as cur:
            for tech, months in data.items():
                # activity per month = job_count nếu có, không thì article_count
                activity = {
                    ym: (counts[0] if counts[0] > 0 else counts[1])
                    for ym, counts in months.items()
                }
                for ym_str, counts in months.items():
                    month_date = _parse_ym(ym_str)
                    if not month_date:
                        continue

                    job_cnt = counts[0]
                    art_cnt = counts[1]
                    act = activity[ym_str]

                    prev_ym = _prev_month(ym_str)
                    prev_act = activity.get(prev_ym, 0)
                    yoy_ym = _year_ago(ym_str)
                    yoy_act = activity.get(yoy_ym, 0)

                    mom = _growth(act, prev_act)
                    yoy = _growth(act, yoy_act)
                    rank = rank_map.get(tech) if ym_str == current_ym else None

                    cur.execute(
                        """INSERT INTO tech_analytics
                           (technology_name, month, job_count, article_count,
                            growth_rate, yoy_growth, mom_growth, ranking)
                           VALUES (%s,%s,%s,%s,%s,%s,%s,%s)
                           ON CONFLICT (technology_name, month) DO UPDATE SET
                             job_count     = EXCLUDED.job_count,
                             article_count = EXCLUDED.article_count,
                             growth_rate   = EXCLUDED.growth_rate,
                             yoy_growth    = EXCLUDED.yoy_growth,
                             mom_growth    = EXCLUDED.mom_growth,
                             ranking       = EXCLUDED.ranking""",
                        (tech, month_date, job_cnt, art_cnt, mom, yoy, mom, rank),
                    )
                    rows_upserted += 1

        pg_conn.commit()
        logger.info("Gold PG ETL: upserted {} rows into tech_analytics", rows_upserted)
        log_pipeline_run(pg_conn, "gold_pg_etl", "success",
                         rows_affected=rows_upserted, run_id=run_id)
        return rows_upserted

    except Exception as exc:
        logger.exception("Gold PG ETL failed")
        try:
            log_pipeline_run(pg_conn, "gold_pg_etl", "failed", error_msg=str(exc), run_id=run_id)
        except Exception:
            pass
        raise
    finally:
        pg_conn.close()


def _prev_month(ym: str) -> str:
    year, month = int(ym[:4]), int(ym[5:7])
    if month == 1:
        return f"{year - 1}-12"
    return f"{year}-{month - 1:02d}"


def _year_ago(ym: str) -> str:
    year = int(ym[:4])
    return f"{year - 1}{ym[4:]}"
