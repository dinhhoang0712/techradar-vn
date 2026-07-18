"""
Các scheduled job của Data Platform.
Mỗi job được gọi bởi APScheduler và tự manage exception.
"""
import time

import requests
from loguru import logger

from config import Settings


def job_neo4j_article_sync(settings: Settings) -> None:
    """Đồng bộ Article/Technology/Company từ dp_processed_articles (Postgres) sang Neo4j."""
    logger.info("=== JOB: neo4j_article_sync ===")
    try:
        from gold.neo4j_article_sync import run
        rows = run(settings)
        logger.info("neo4j_article_sync: {} articles synced", rows)
    except Exception:
        logger.exception("neo4j_article_sync FAILED")


def job_neo4j_job_sync(settings: Settings) -> None:
    """Đồng bộ Job/Company từ dp_processed_jobs (Postgres) sang Neo4j."""
    logger.info("=== JOB: neo4j_job_sync ===")
    try:
        from gold.neo4j_job_sync import run
        rows = run(settings)
        logger.info("neo4j_job_sync: {} jobs synced", rows)
    except Exception:
        logger.exception("neo4j_job_sync FAILED")


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


def job_tech_dedup(settings: Settings) -> None:
    """
    Gộp :Technology node trùng lặp do khác cách viết (Go/Golang,
    ML/Machine Learning, K8s/Kubernetes...) — chạy sau neo4j_enricher.
    """
    logger.info("=== JOB: tech_dedup ===")
    try:
        from gold.tech_dedup import run
        results = run(settings)
        logger.info("tech_dedup: {}", results)
    except Exception:
        logger.exception("tech_dedup FAILED")


def job_retrain_clustering(settings: Settings) -> None:
    """
    Trigger ml-clustering pipeline retrain sau khi neo4j_enricher đã cập nhật graph.
    Pipeline chạy async trong ml-clustering service (5 DVC stages) — sau khi trigger
    thành công, job này BLOCK lại, poll GET /pipeline/status định kỳ cho tới khi pipeline
    xong (success/failed) hoặc hết clustering_retrain_max_wait_s, rồi mới ghi kết quả THẬT
    vào dp_pipeline_runs (giống job_embed_trigger) — trước đây job chỉ log việc gọi HTTP
    trigger có thành công hay không, không bao giờ biết 5 stage có thật sự chạy xong không,
    nên 1 lần retrain lỗi (vd hết quota LLM ở Stage 4) sẽ nằm im lặng trong log ml-clustering,
    không ai biết. Block tối đa clustering_retrain_max_wait_s (mặc định 2h) là chấp nhận được
    vì job này vốn chỉ chạy 1 lần/tuần và misfire_grace_time đã cấu hình rộng cho đúng lý do này.
    """
    logger.info("=== JOB: retrain_clustering ===")
    from common.db import get_pg_conn, log_pipeline_run

    pg_conn = get_pg_conn(settings)
    run_id = log_pipeline_run(pg_conn, "retrain_clustering", "running")
    base_url = settings.ml_clustering_base_url.rstrip("/")
    trigger_url = f"{base_url}/pipeline/trigger"
    status_url = f"{base_url}/pipeline/status"

    try:
        resp = requests.post(
            trigger_url,
            headers={"X-Internal-Auth": settings.internal_api_token},
            timeout=10,
        )
        resp.raise_for_status()
        logger.info("retrain_clustering: {}", resp.json().get("message"))
    except requests.exceptions.ConnectionError:
        logger.warning("retrain_clustering: ml-clustering không reach được tại {}", trigger_url)
        log_pipeline_run(pg_conn, "retrain_clustering", "failed",
                         error_msg=f"ml-clustering không reach được tại {trigger_url}", run_id=run_id)
        pg_conn.close()
        return
    except requests.exceptions.HTTPError as e:
        if e.response.status_code == 409:
            logger.warning("retrain_clustering: pipeline đang chạy, bỏ qua lần này")
            log_pipeline_run(pg_conn, "retrain_clustering", "failed",
                             error_msg="Bỏ qua: pipeline đã đang chạy từ lần trigger khác", run_id=run_id)
        else:
            logger.error("retrain_clustering: HTTP {} từ {}", e.response.status_code, trigger_url)
            log_pipeline_run(pg_conn, "retrain_clustering", "failed",
                             error_msg=f"HTTP {e.response.status_code} từ {trigger_url}", run_id=run_id)
        pg_conn.close()
        return
    except Exception as exc:
        logger.exception("retrain_clustering: trigger FAILED")
        log_pipeline_run(pg_conn, "retrain_clustering", "failed", error_msg=str(exc), run_id=run_id)
        pg_conn.close()
        return

    # Trigger thành công — poll /pipeline/status cho tới khi pipeline thật sự xong.
    elapsed_s = 0
    final_state: dict | None = None
    while elapsed_s < settings.clustering_retrain_max_wait_s:
        time.sleep(settings.clustering_retrain_poll_interval_s)
        elapsed_s += settings.clustering_retrain_poll_interval_s
        try:
            state = requests.get(status_url, timeout=10).json()
        except Exception as exc:
            logger.warning("retrain_clustering: poll /pipeline/status thất bại ({}), thử lại...", exc)
            continue
        if state.get("status") in ("success", "failed"):
            final_state = state
            break

    if final_state is None:
        msg = f"Timeout sau {settings.clustering_retrain_max_wait_s}s chờ pipeline hoàn tất — không rõ kết quả cuối cùng"
        logger.warning("retrain_clustering: {}", msg)
        log_pipeline_run(pg_conn, "retrain_clustering", "failed", error_msg=msg, run_id=run_id)
    elif final_state["status"] == "success":
        logger.info("retrain_clustering: hoàn tất sau {}s", final_state.get("duration_s"))
        log_pipeline_run(pg_conn, "retrain_clustering", "success", run_id=run_id)
    else:
        error_msg = (final_state.get("error") or "unknown")[:500]
        logger.error("retrain_clustering: pipeline báo failed — {}", error_msg)
        log_pipeline_run(pg_conn, "retrain_clustering", "failed", error_msg=error_msg, run_id=run_id)
    pg_conn.close()


def job_embed_trigger(settings: Settings) -> None:
    """
    Gọi ai-rag-core POST /embed/trigger để embed các Article mới trong Neo4j.
    ai-rag-core chạy job ở background, response ngay lập tức — nên "success" ở đây
    chỉ có nghĩa là đã gửi yêu cầu thành công, không phải embed đã xong (ai-rag-core
    không có endpoint status riêng để theo dõi tiếp).

    Ghi dp_pipeline_runs (giống các job gold/*.py khác) để admin panel có trạng thái
    lần chạy gần nhất, thay vì chỉ nằm trong log.
    """
    logger.info("=== JOB: embed_trigger ===")
    from common.db import get_pg_conn, log_pipeline_run

    pg_conn = get_pg_conn(settings)
    run_id = log_pipeline_run(pg_conn, "embed_trigger", "running")
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
        log_pipeline_run(pg_conn, "embed_trigger", "success", run_id=run_id)
    except requests.exceptions.ConnectionError:
        logger.warning("embed_trigger: ai-rag-core không reach được tại {}", url)
        log_pipeline_run(pg_conn, "embed_trigger", "failed",
                         error_msg=f"ai-rag-core không reach được tại {url}", run_id=run_id)
    except requests.exceptions.HTTPError as e:
        logger.error("embed_trigger: HTTP {} từ {}", e.response.status_code, url)
        log_pipeline_run(pg_conn, "embed_trigger", "failed",
                         error_msg=f"HTTP {e.response.status_code} từ {url}", run_id=run_id)
    except Exception as exc:
        logger.exception("embed_trigger FAILED")
        log_pipeline_run(pg_conn, "embed_trigger", "failed", error_msg=str(exc), run_id=run_id)
    finally:
        pg_conn.close()
