"""
Data Platform — entry point.
Chạy 3 thành phần song song:
  Thread 1 — Bronze Writer   (Kafka raw_* → MinIO)
  Thread 2 — Silver Processor (Kafka extracted_* → PostgreSQL)
  Main     — APScheduler      (Gold ETL + Enricher + Embed trigger)
"""
import signal
import sys
import threading
import time

from loguru import logger

from common.logger import configure_logging
from config import get_settings


def main() -> None:
    configure_logging()
    settings = get_settings()

    logger.info("Data Platform starting up...")
    logger.info("  Kafka    : {}", settings.kafka_bootstrap_servers)
    logger.info("  MinIO    : {}", settings.minio_endpoint)
    logger.info("  Postgres : {}", settings.postgres_dsn.split("@")[-1])
    logger.info("  Neo4j    : {}", settings.neo4j_uri)

    # ── Bronze Writer thread ───────────────────────────────────────────────
    from bronze.writer import run as run_bronze

    t_bronze = threading.Thread(
        target=_safe_run,
        args=("Bronze Writer", run_bronze, settings),
        daemon=True,
        name="bronze-writer",
    )

    # ── Silver Processor thread ────────────────────────────────────────────
    from silver.processor import run as run_silver

    t_silver = threading.Thread(
        target=_safe_run,
        args=("Silver Processor", run_silver, settings),
        daemon=True,
        name="silver-processor",
    )

    t_bronze.start()
    t_silver.start()
    logger.info("Bronze Writer and Silver Processor threads started.")

    # ── APScheduler (main thread) ──────────────────────────────────────────
    from scheduler.scheduler import create_scheduler

    scheduler = create_scheduler(settings)
    scheduler.start()
    logger.info("Scheduler started. Next runs:")
    for job in scheduler.get_jobs():
        logger.info("  {} — next: {}", job.name, job.next_run_time)

    if settings.run_jobs_on_start:
        logger.info("RUN_JOBS_ON_START=true — triggering all jobs immediately for initial seed...")
        for job in scheduler.get_jobs():
            scheduler.modify_job(job.id, next_run_time=__import__("datetime").datetime.now())

    # ── Graceful shutdown ──────────────────────────────────────────────────
    stop_event = threading.Event()

    def _shutdown(signum, frame):
        logger.info("Data Platform: shutdown signal received, stopping...")
        scheduler.shutdown(wait=False)
        stop_event.set()

    signal.signal(signal.SIGTERM, _shutdown)
    signal.signal(signal.SIGINT, _shutdown)

    try:
        while not stop_event.is_set():
            time.sleep(5)
            # Restart threads if they died unexpectedly
            if not t_bronze.is_alive():
                logger.warning("Bronze Writer thread died, restarting...")
                t_bronze = threading.Thread(
                    target=_safe_run,
                    args=("Bronze Writer", run_bronze, settings),
                    daemon=True,
                    name="bronze-writer",
                )
                t_bronze.start()
            if not t_silver.is_alive():
                logger.warning("Silver Processor thread died, restarting...")
                t_silver = threading.Thread(
                    target=_safe_run,
                    args=("Silver Processor", run_silver, settings),
                    daemon=True,
                    name="silver-processor",
                )
                t_silver.start()
    except KeyboardInterrupt:
        _shutdown(None, None)

    logger.info("Data Platform shut down cleanly.")
    sys.exit(0)


def _safe_run(name: str, fn, *args) -> None:
    """Wrapper: log exception và exit thread với delay để tránh tight restart loop."""
    try:
        fn(*args)
    except Exception:
        logger.exception("{} crashed", name)
    finally:
        logger.warning("{} thread exiting", name)
        time.sleep(3)


if __name__ == "__main__":
    main()
