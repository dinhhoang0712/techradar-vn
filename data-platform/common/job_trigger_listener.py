"""
Job Trigger Listener — cho phép admin (Java backend) chạy ngay 1 job đã đăng ký
trong APScheduler thay vì chờ lịch cron, qua Redis Pub/Sub.

Vì sao Redis Pub/Sub thay vì mở HTTP server riêng: data-platform không có lý do
để expose port (không phục vụ request nào khác), và Redis đã được dùng đúng mục
đích này cho crawler (xem CrawlerAdminController.java bên Java) — tái dùng cùng
cơ chế cho nhất quán, không thêm dependency mới ngoài redis-py.

Cơ chế trigger: gọi lại đúng scheduler.modify_job(job_id, next_run_time=now()) —
CÙNG cơ chế RUN_JOBS_ON_START đã dùng trong main.py. An toàn vì mỗi job đã có
max_instances=1 (APScheduler tự bỏ qua nếu job đang chạy dở).

Chỉ nhận trigger cho các job KHÔNG đã có nút riêng: gold_pg_etl (đã có
/admin/analytics/rebuild — bản Java tự viết lại) và retrain_clustering (đã có
/admin/clustering/pipeline/trigger — proxy sang ml-clustering) bị loại khỏi
whitelist để tránh 2 nguồn sự thật cho cùng 1 job.
"""

from __future__ import annotations

import json
from datetime import datetime

import redis
from apscheduler.schedulers.background import BackgroundScheduler
from config import Settings
from loguru import logger

TRIGGER_CHANNEL = "data-platform:trigger"

ALLOWED_JOB_IDS = {
    "neo4j_article_sync",
    "neo4j_job_sync",
    "neo4j_enricher",
    "tech_dedup",
    "embed_trigger",
}


def _handle_trigger(scheduler: BackgroundScheduler, raw_payload: str) -> None:
    try:
        payload = json.loads(raw_payload)
        job_id = payload.get("jobId")
    except (json.JSONDecodeError, AttributeError):
        logger.warning("Job Trigger Listener: payload không hợp lệ: {}", raw_payload)
        return

    if job_id not in ALLOWED_JOB_IDS:
        logger.warning("Job Trigger Listener: jobId '{}' không nằm trong whitelist, bỏ qua", job_id)
        return

    if scheduler.get_job(job_id) is None:
        logger.warning("Job Trigger Listener: jobId '{}' không tồn tại trong scheduler, bỏ qua", job_id)
        return

    # Must be timezone-aware in the scheduler's own tz (Asia/Ho_Chi_Minh) — a naive datetime.now()
    # here gets localized as if it already were that tz, landing ~7h in the past on a UTC host and
    # silently misfiring (misfire_grace_time=3600 is far smaller than that gap).
    scheduler.modify_job(job_id, next_run_time=datetime.now(scheduler.timezone))
    logger.info("Job Trigger Listener: đã trigger '{}' chạy ngay", job_id)


def run(settings: Settings, scheduler: BackgroundScheduler) -> None:
    client = redis.from_url(settings.redis_url, decode_responses=True)
    pubsub = client.pubsub(ignore_subscribe_messages=True)
    pubsub.subscribe(TRIGGER_CHANNEL)
    logger.info("Job Trigger Listener: đã subscribe kênh '{}'", TRIGGER_CHANNEL)

    for message in pubsub.listen():
        if message.get("type") != "message":
            continue
        _handle_trigger(scheduler, message.get("data", ""))
