"""
Gold — Neo4j Enricher
Chạy sau khi KafkaNeo4jWriterService đã ghi các node cơ bản vào Neo4j.
Nhiệm vụ: tạo các derived relationship và cập nhật statistics mà realtime
writer không làm được.

Chạy: mỗi đêm lúc 5:00 AM.
"""
from loguru import logger

from common.db import get_neo4j_driver, get_pg_conn, log_pipeline_run
from config import Settings

# (Company)-[:USES]->(Technology): suy ra từ bài viết đề cập cả company lẫn tech
_COMPANY_USES_TECH = """
MATCH (a:Article)-[:MENTIONS]->(c:Company)
MATCH (a)-[:MENTIONS]->(t:Technology)
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


def run(settings: Settings) -> dict:
    logger.info("Neo4j Enricher: starting...")

    pg_conn = get_pg_conn(settings)
    run_id = log_pipeline_run(pg_conn, "neo4j_enricher", "running")
    results = {}

    try:
        driver = get_neo4j_driver(settings)

        with driver.session() as session:
            logger.info("Neo4j Enricher: creating (Company)-[:USES]->(Technology)...")
            rec = session.run(_COMPANY_USES_TECH).single()
            results["company_uses_tech"] = rec["cnt"] if rec else 0

            logger.info("Neo4j Enricher: creating (Technology)-[:RELATED_TO]->(Technology)...")
            rec = session.run(_TECH_RELATED_TO).single()
            results["tech_related_to"] = rec["cnt"] if rec else 0

            logger.info("Neo4j Enricher: updating Technology mention counts...")
            rec = session.run(_UPDATE_TECH_MENTION_COUNT).single()
            results["tech_mention_update"] = rec["cnt"] if rec else 0

            logger.info("Neo4j Enricher: updating Technology trend scores...")
            rec = session.run(_UPDATE_TREND_SCORE).single()
            results["trend_score_update"] = rec["cnt"] if rec else 0

        driver.close()

        total = sum(results.values())
        logger.info("Neo4j Enricher: done — {}", results)
        log_pipeline_run(pg_conn, "neo4j_enricher", "success",
                         rows_affected=total, run_id=run_id)
        return results

    except Exception as exc:
        logger.exception("Neo4j Enricher failed")
        try:
            log_pipeline_run(pg_conn, "neo4j_enricher", "failed",
                             error_msg=str(exc), run_id=run_id)
        except Exception:
            pass
        raise
    finally:
        pg_conn.close()
