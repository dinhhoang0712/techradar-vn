"""
Gold — Neo4j Enricher
Chạy sau khi KafkaNeo4jWriterService đã ghi các node cơ bản vào Neo4j.
Nhiệm vụ: tạo các derived relationship và cập nhật statistics mà realtime
writer không làm được.

Chạy: mỗi đêm lúc 5:00 AM.
"""

from common.db import get_neo4j_driver, get_pg_conn, log_pipeline_run
from config import Settings
from loguru import logger

# (Company)-[:USES]->(Technology): suy ra từ bài viết đề cập cả company lẫn tech.
#
# Phát hiện thật (KG Health Audit mở rộng, xác nhận sống): tín hiệu này gần như không xảy ra —
# chỉ 6/425 Company từng được 1 Article nhắc tên (MENTIONS), 419 công ty còn lại CHỈ tồn tại qua
# Job posting nên KHÔNG BAO GIỜ có cạnh USES nào dù thực tế đang dùng rất nhiều công nghệ. Kết
# quả: 46 cạnh USES thay vì hàng nghìn (snapshot cũ trên Aura có ~11.3k, xem docs/DATABASE.md
# §4.1) — cả `services/ai-rag-core`'s `COMPANIES_USING_TECH` (câu hỏi kiểu "công ty nào dùng
# React?") lẫn `services/ml-clustering`'s `neo4j_loader.py` (dùng USES làm feature huấn luyện
# cluster) đều đang đói dữ liệu vì việc này.
_COMPANY_USES_TECH_FROM_ARTICLE = """
MATCH (a:Article)-[:MENTIONS]->(c:Company)
MATCH (a)-[:MENTIONS]->(t:Technology)
MERGE (c)-[r:USES]->(t)
ON CREATE SET r.evidence_count = 1, r.first_seen = date()
ON MATCH  SET r.evidence_count = r.evidence_count + 1,
              r.last_updated = date()
RETURN count(r) AS cnt
"""

# Tín hiệu thứ 2, bổ sung: công ty đang tuyển vị trí yêu cầu công nghệ X — tức công ty đó đang
# dùng X. Cùng pattern `Company<-[:POSTED_BY|HIRES_FOR]-Job-[:REQUIRES]->Technology` mà
# `apps/backend`'s `Neo4jCompanyRepository`/`COMPANY_INSIGHT_CONTEXT` đã tin dùng để suy ra tech
# stack công ty (xem docs/DATABASE.md §4.1) — đây là nguồn tín hiệu chính, vì hầu hết Company chỉ
# xuất hiện qua Job, không qua Article. MERGE cùng 1 cạnh USES với query trên (không tạo type
# quan hệ mới) — evidence_count cộng dồn nếu cả 2 tín hiệu cùng xác nhận 1 cặp Company-Technology.
_COMPANY_USES_TECH_FROM_JOB = """
MATCH (c:Company)<-[:POSTED_BY|HIRES_FOR]-(j:Job)-[:REQUIRES]->(t:Technology)
MERGE (c)-[r:USES]->(t)
ON CREATE SET r.evidence_count = 1, r.first_seen = date()
ON MATCH  SET r.evidence_count = r.evidence_count + 1,
              r.last_updated = date()
RETURN count(r) AS cnt
"""

# (Technology)-[:RELATED_TO]->(Technology): co-mention trong cùng bài viết
_TECH_RELATED_TO = """
MATCH (a:Article)-[:MENTIONS]->(t1:Technology)
MATCH (a)-[:MENTIONS]->(t2:Technology)
WHERE id(t1) < id(t2)
MERGE (t1)-[r:RELATED_TO]->(t2)
ON CREATE SET r.co_mention_count = 1
ON MATCH  SET r.co_mention_count = r.co_mention_count + 1
RETURN count(r) AS cnt
"""

# Cập nhật mention_count trên Technology nodes
_UPDATE_TECH_MENTION_COUNT = """
MATCH (t:Technology)
OPTIONAL MATCH (t)<-[:MENTIONS]-(a:Article)
OPTIONAL MATCH (t)<-[:REQUIRES]-(j:Job)
WITH t,
     count(DISTINCT a) AS article_cnt,
     count(DISTINCT j) AS job_cnt
SET t.article_count = article_cnt,
    t.job_count     = job_cnt,
    t.mention_count = article_cnt + job_cnt,
    t.last_updated  = date()
RETURN count(t) AS cnt
"""

# Cập nhật trend_score đơn giản: (job_count * 2 + article_count) / max
_UPDATE_TREND_SCORE = """
MATCH (t:Technology)
WHERE t.mention_count IS NOT NULL AND t.mention_count > 0
WITH max(t.mention_count) AS max_count
MATCH (t:Technology)
WHERE t.mention_count IS NOT NULL
SET t.trend_score = round(toFloat(t.mention_count * 2 + coalesce(t.job_count, 0))
                    / (max_count * 3 + 1) * 100) / 100
RETURN count(t) AS cnt
"""

# Ghi category (do tech_dedup.py MVP + gold/tech_category_backfill.py tính, lưu ở Postgres
# dp_tech_category) vào Technology node — hoàn thiện field đã document ở docs/DATABASE.md §4.1
# nhưng trước đây chưa từng có writer nào ghi. Đọc lại dp_tech_category mỗi lần chạy nên tự
# nhặt category mới nhất, kể cả category ghi đêm trước bởi tech_dedup (03:30, sau enricher
# 03:00) — độ trễ tối đa ~24h, không phải bug.
_UPDATE_TECH_CATEGORY = """
UNWIND $rows AS row
MATCH (t:Technology {name: row.canonical_name})
SET t.category = row.category
RETURN count(t) AS cnt
"""


def _fetch_categories(pg_conn) -> list[dict]:
    with pg_conn.cursor() as cur:
        cur.execute("SELECT canonical_name, category FROM dp_tech_category")
        return cur.fetchall()


def run(settings: Settings) -> dict:
    logger.info("Neo4j Enricher: starting...")

    pg_conn = get_pg_conn(settings)
    run_id = log_pipeline_run(pg_conn, "neo4j_enricher", "running")
    results = {}

    try:
        driver = get_neo4j_driver(settings)

        with driver.session() as session:
            logger.info("Neo4j Enricher: creating (Company)-[:USES]->(Technology) từ Article...")
            rec_article = session.run(_COMPANY_USES_TECH_FROM_ARTICLE).single()

            logger.info("Neo4j Enricher: creating (Company)-[:USES]->(Technology) từ Job...")
            rec_job = session.run(_COMPANY_USES_TECH_FROM_JOB).single()

            results["company_uses_tech"] = (rec_article["cnt"] if rec_article else 0) + (
                rec_job["cnt"] if rec_job else 0
            )

            logger.info("Neo4j Enricher: creating (Technology)-[:RELATED_TO]->(Technology)...")
            rec = session.run(_TECH_RELATED_TO).single()
            results["tech_related_to"] = rec["cnt"] if rec else 0

            logger.info("Neo4j Enricher: updating Technology mention counts...")
            rec = session.run(_UPDATE_TECH_MENTION_COUNT).single()
            results["tech_mention_update"] = rec["cnt"] if rec else 0

            logger.info("Neo4j Enricher: updating Technology trend scores...")
            rec = session.run(_UPDATE_TREND_SCORE).single()
            results["trend_score_update"] = rec["cnt"] if rec else 0

            logger.info("Neo4j Enricher: updating Technology categories from dp_tech_category...")
            categories = _fetch_categories(pg_conn)
            rec = session.run(_UPDATE_TECH_CATEGORY, {"rows": categories}).single() if categories else None
            results["category_update"] = rec["cnt"] if rec else 0

        driver.close()

        total = sum(results.values())
        logger.info("Neo4j Enricher: done — {}", results)
        log_pipeline_run(pg_conn, "neo4j_enricher", "success", rows_affected=total, run_id=run_id)
        return results

    except Exception as exc:
        logger.exception("Neo4j Enricher failed")
        try:
            log_pipeline_run(pg_conn, "neo4j_enricher", "failed", error_msg=str(exc), run_id=run_id)
        except Exception:
            pass
        raise
    finally:
        pg_conn.close()
