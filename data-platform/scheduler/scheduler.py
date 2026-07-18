"""
APScheduler setup cho Data Platform.
Schedule mặc định (Asia/Ho_Chi_Minh):
  02:00 daily   — Neo4j Article Sync (dp_processed_articles → Neo4j Article/Technology)
  02:30 daily   — Neo4j Job Sync (dp_processed_jobs → Neo4j Job/Company)
  03:00 daily   — Gold PG ETL (rebuild tech_analytics)
  04:00 daily   — Embed Trigger (gọi ai-rag-core /embed/trigger)
  05:00 daily   — Neo4j Enricher (derived relationships + stats)
  05:30 daily   — Tech Dedup (gộp Technology node trùng lặp: Go/Golang...)
  06:00 weekly  — Clustering Retrain (trigger ml-clustering pipeline, Chủ nhật)
"""
from apscheduler.schedulers.background import BackgroundScheduler
from apscheduler.triggers.cron import CronTrigger
from loguru import logger

from config import Settings
from scheduler.jobs import (
    job_embed_trigger,
    job_gold_pg_etl,
    job_neo4j_article_sync,
    job_neo4j_enricher,
    job_neo4j_job_sync,
    job_retrain_clustering,
    job_tech_dedup,
)

TZ = "Asia/Ho_Chi_Minh"


def create_scheduler(settings: Settings) -> BackgroundScheduler:
    scheduler = BackgroundScheduler(timezone=TZ)

    scheduler.add_job(
        func=job_neo4j_article_sync,
        trigger=CronTrigger(
            hour=settings.article_sync_hour,
            minute=settings.article_sync_minute,
            timezone=TZ,
        ),
        args=[settings],
        id="neo4j_article_sync",
        name="Neo4j Article Sync",
        max_instances=1,
        replace_existing=True,
        misfire_grace_time=3600,
    )

    scheduler.add_job(
        func=job_neo4j_job_sync,
        trigger=CronTrigger(
            hour=settings.job_sync_hour,
            minute=settings.job_sync_minute,
            timezone=TZ,
        ),
        args=[settings],
        id="neo4j_job_sync",
        name="Neo4j Job Sync",
        max_instances=1,
        replace_existing=True,
        misfire_grace_time=3600,
    )

    scheduler.add_job(
        func=job_gold_pg_etl,
        trigger=CronTrigger(
            hour=settings.gold_etl_hour,
            minute=settings.gold_etl_minute,
            timezone=TZ,
        ),
        args=[settings],
        id="gold_pg_etl",
        name="Gold PG ETL",
        max_instances=1,
        replace_existing=True,
        misfire_grace_time=3600,
    )

    scheduler.add_job(
        func=job_neo4j_enricher,
        trigger=CronTrigger(
            hour=settings.neo4j_enricher_hour,
            minute=settings.neo4j_enricher_minute,
            timezone=TZ,
        ),
        args=[settings],
        id="neo4j_enricher",
        name="Neo4j Enricher",
        max_instances=1,
        replace_existing=True,
        misfire_grace_time=3600,
    )

    scheduler.add_job(
        func=job_tech_dedup,
        trigger=CronTrigger(
            hour=settings.tech_dedup_hour,
            minute=settings.tech_dedup_minute,
            timezone=TZ,
        ),
        args=[settings],
        id="tech_dedup",
        name="Tech Dedup",
        max_instances=1,
        replace_existing=True,
        misfire_grace_time=3600,
    )

    scheduler.add_job(
        func=job_embed_trigger,
        trigger=CronTrigger(
            hour=settings.embed_trigger_hour,
            minute=settings.embed_trigger_minute,
            timezone=TZ,
        ),
        args=[settings],
        id="embed_trigger",
        name="Embed Trigger",
        max_instances=1,
        replace_existing=True,
        misfire_grace_time=3600,
    )

    scheduler.add_job(
        func=job_retrain_clustering,
        trigger=CronTrigger(
            day_of_week=settings.clustering_retrain_day_of_week,
            hour=settings.clustering_retrain_hour,
            minute=settings.clustering_retrain_minute,
            timezone=TZ,
        ),
        args=[settings],
        id="retrain_clustering",
        name="Clustering Retrain",
        max_instances=1,
        replace_existing=True,
        misfire_grace_time=7200,
    )

    logger.info(
        "Scheduler configured: neo4j_article_sync={}:{:02d}, gold_pg_etl={}:{:02d}, "
        "neo4j_enricher={}:{:02d}, embed={}:{:02d}, retrain_clustering={} {}:{:02d}",
        settings.article_sync_hour, settings.article_sync_minute,
        settings.gold_etl_hour, settings.gold_etl_minute,
        settings.neo4j_enricher_hour, settings.neo4j_enricher_minute,
        settings.embed_trigger_hour, settings.embed_trigger_minute,
        settings.clustering_retrain_day_of_week,
        settings.clustering_retrain_hour, settings.clustering_retrain_minute,
    )

    return scheduler
