"""
Gold — Neo4j Article Sync
Đọc dp_processed_articles (Postgres, silver layer) → MERGE Article/Technology/
Company/Location + MENTIONS relationship vào Neo4j.

Job này lấp khoảng trống tương tự neo4j_job_sync.py nhưng cho Article: đường đi
raw_articles → (Spring Boot KafkaExtractorService NLP) → extracted_articles →
KafkaNeo4jWriterService chỉ ghi Article vào graph khi message còn tồn tại trong
Kafka topic raw_articles lúc Spring Boot chạy — Kafka retention xoá message cũ
trước khi kịp xử lý hết, nên phần lớn bài đã crawl nằm nguyên trong
dp_processed_articles nhưng chưa từng tới graph. Job này đồng bộ trực tiếp từ
Postgres, độc lập với Kafka, dùng chung id scheme (md5(source_url), khớp với
generateId() bên Java) nên hai đường ghi không tạo node trùng.

Vì entity_techs chỉ được NLP service (Spring Boot) điền — và NLP service hiếm
khi chạy được tới nơi — job này còn tự trích technology bằng keyword matching
(common.tech_keywords) trên title+content làm nguồn bổ sung, hợp với
entity_techs đã có sẵn (nếu có).

Chạy: mỗi đêm, trước gold_pg_etl (cấu hình trong scheduler).
"""
from __future__ import annotations

import re

from loguru import logger

from common.db import get_pg_conn, get_neo4j_driver, log_pipeline_run
from common.tech_keywords import extract_tech
from config import Settings

BATCH_SIZE = 500

_SELECT_ARTICLES = """
SELECT id, source_url, source_platform, title, content, published_at,
       entity_techs, entity_orgs, entity_locs
FROM dp_processed_articles
WHERE is_duplicate = false AND status = 'processed'
"""

_MERGE_ARTICLES = """
UNWIND $rows AS row
MERGE (a:Article {id: row.id})
SET a.title = row.title,
    a.content = row.content,
    a.url = row.source_url,
    a.source_platform = row.source_platform,
    a.published_date = row.published_date
"""

_MERGE_TECHS = """
UNWIND $rows AS row
UNWIND row.techs AS tech
MATCH (a:Article {id: row.id})
MERGE (t:Technology {name: tech})
MERGE (a)-[r:MENTIONS]->(t)
ON CREATE SET t.mention_count = coalesce(t.mention_count, 0) + 1
"""

_MERGE_ORGS = """
UNWIND $rows AS row
UNWIND row.orgs AS org
MATCH (a:Article {id: row.id})
MERGE (c:Company {id: org.id})
SET c.name = org.name
MERGE (a)-[:MENTIONS]->(c)
"""

_MERGE_LOCS = """
UNWIND $rows AS row
UNWIND row.locs AS loc
MATCH (a:Article {id: row.id})
MERGE (l:Location {name: loc})
MERGE (a)-[:MENTIONS]->(l)
"""


def _slugify(value: str) -> str:
    value = (value or "").strip().lower()
    value = re.sub(r"[^a-z0-9]+", "-", value)
    return value.strip("-")


def _chunks(items: list, size: int):
    for i in range(0, len(items), size):
        yield items[i:i + size]


def run(settings: Settings) -> int:
    logger.info("Neo4j Article Sync: starting...")

    pg_conn = get_pg_conn(settings)
    run_id = log_pipeline_run(pg_conn, "neo4j_article_sync", "running")

    try:
        with pg_conn.cursor() as cur:
            cur.execute(_SELECT_ARTICLES)
            articles = cur.fetchall()

        rows = []
        for article in articles:
            published_date = None
            if article["published_at"]:
                published_date = article["published_at"].strftime("%Y-%m-%d")

            techs = set(article["entity_techs"] or [])
            techs |= set(extract_tech(f"{article['title'] or ''} {article['content'] or ''}"))

            orgs = []
            for org_name in (article["entity_orgs"] or []):
                org_name = (org_name or "").strip()
                if org_name:
                    orgs.append({"id": _slugify(org_name), "name": org_name})

            locs = [loc.strip() for loc in (article["entity_locs"] or []) if loc and loc.strip()]

            rows.append({
                "id": article["id"],
                "title": article["title"],
                "content": article["content"],
                "source_url": article["source_url"],
                "source_platform": article["source_platform"],
                "published_date": published_date,
                "techs": sorted(techs),
                "orgs": orgs,
                "locs": locs,
            })

        driver = get_neo4j_driver(settings)
        with driver.session() as session:
            for batch in _chunks(rows, BATCH_SIZE):
                session.run(_MERGE_ARTICLES, rows=batch)
                session.run(_MERGE_TECHS, rows=batch)
                session.run(_MERGE_ORGS, rows=batch)
                session.run(_MERGE_LOCS, rows=batch)
        driver.close()

        logger.info("Neo4j Article Sync: upserted {} articles", len(rows))
        log_pipeline_run(pg_conn, "neo4j_article_sync", "success",
                         rows_affected=len(rows), run_id=run_id)
        return len(rows)

    except Exception as exc:
        logger.exception("Neo4j Article Sync failed")
        try:
            log_pipeline_run(pg_conn, "neo4j_article_sync", "failed",
                             error_msg=str(exc), run_id=run_id)
        except Exception:
            pass
        raise
    finally:
        pg_conn.close()
