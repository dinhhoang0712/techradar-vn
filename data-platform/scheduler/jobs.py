"""
Các scheduled job của Data Platform.
Mỗi job được gọi bởi APScheduler và tự manage exception.
"""
import requests
from loguru import logger

from config import Settings


def job_gold_pg_etl(settings: Settings) -> None:
    """Rebuild tech_analytics từ Neo4j → PostgreSQL."""
    logger.info("=== JOB: gold_pg_etl ===")
    try:
        from gold.pg_etl import run
        rows = run(settings)
        logger.info("gold_pg_etl: {} rows upserted", rows)
    except Exception:
        logger.exception("gold_pg_etl FAILED")


def job_neo4j_enricher(settings: Settings) -> None:
    """Tạo derived relationships và cập nhật statistics trong Neo4j."""
    logger.info("=== JOB: neo4j_enricher ===")
    try:
        from gold.neo4j_enricher import run
        results = run(settings)
        logger.info("neo4j_enricher: {}", results)
    except Exception:
        logger.exception("neo4j_enricher FAILED")


def job_retrain_clustering(settings: Settings) -> None:
    """
    Trigger ml-clustering pipeline retrain sau khi neo4j_enricher đã cập nhật graph.
    Pipeline chạy async trong ml-clustering service (5 DVC stages).
    """
    logger.info("=== JOB: retrain_clustering ===")
    url = f"{settings.ml_clustering_base_url.rstrip('/')}/pipeline/trigger"
    try:
        resp = requests.post(
            url,
            headers={"X-Internal-Auth": settings.internal_api_token},
            timeout=10,
        )
        resp.raise_for_status()
        body = resp.json()
        logger.info("retrain_clustering: {}", body.get("message"))
    except requests.exceptions.ConnectionError:
        logger.warning("retrain_clustering: ml-clustering không reach được tại {}", url)
    except requests.exceptions.HTTPError as e:
        if e.response.status_code == 409:
            logger.warning("retrain_clustering: pipeline đang chạy, bỏ qua lần này")
        else:
            logger.error("retrain_clustering: HTTP {} từ {}", e.response.status_code, url)
    except Exception:
        logger.exception("retrain_clustering FAILED")


def job_embed_trigger(settings: Settings) -> None:
    """
    Gọi ai-rag-core POST /embed/trigger để embed các Article mới trong Neo4j.
    ai-rag-core chạy job ở background, response ngay lập tức.
    """
    logger.info("=== JOB: embed_trigger ===")
    url = f"{settings.rag_base_url.rstrip('/')}/embed/trigger"
    try:
        resp = requests.post(
            url,
            headers={"X-Embed-Secret": settings.embed_secret},
            timeout=10,
        )
        resp.raise_for_status()
        body = resp.json()
        logger.info("embed_trigger: status={} msg={}", body.get("status"), body.get("message"))
    except requests.exceptions.ConnectionError:
        logger.warning("embed_trigger: ai-rag-core không reach được tại {}", url)
    except requests.exceptions.HTTPError as e:
        logger.error("embed_trigger: HTTP {} từ {}", e.response.status_code, url)
    except Exception:
        logger.exception("embed_trigger FAILED")
