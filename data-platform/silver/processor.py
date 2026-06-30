"""
Silver Processor — Kafka consumer group: silver-processor
Subscribes: extracted_articles, extracted_jobs
Action: Dedup + quality score → ghi vào PostgreSQL Silver catalog.

extracted_* topics được tạo ra bởi Spring Boot KafkaExtractorService.
Group-id silver-processor: KHÔNG xung đột với Spring Boot consumer group.
"""
import hashlib
import json
import time
from datetime import datetime, timezone

from kafka import KafkaConsumer
from kafka.errors import NoBrokersAvailable
from loguru import logger

from common.db import get_pg_conn
from config import Settings
from silver.deduplicator import (
    check_content_duplicate,
    check_job_duplicate,
    content_hash,
)

# Subscribe cả raw_* (từ crawler) lẫn extracted_* (từ Spring Boot NLP).
# Khi Spring Boot chạy, extracted_* có entity đã được extract; raw_* vẫn được xử lý
# song song với entity rỗng để không bỏ lỡ dữ liệu khi Spring Boot tắt.
TOPICS = ["raw_articles", "raw_jobs", "extracted_articles", "extracted_jobs"]
GROUP_ID = "silver-processor"


def _quality_score(title: str, content: str) -> float:
    score = 0.0
    if title and len(title.strip()) >= 10:
        score += 0.3
    if content and len(content.strip()) >= 200:
        score += 0.4
    if content and len(content.strip()) >= 800:
        score += 0.3
    return round(min(score, 1.0), 2)


def _parse_published_at(date_str: str) -> datetime | None:
    if not date_str:
        return None
    for fmt in ("%Y-%m-%d", "%d/%m/%Y", "%Y-%m-%dT%H:%M:%S", "%Y-%m-%d %H:%M:%S"):
        try:
            return datetime.strptime(date_str[:19], fmt).replace(tzinfo=timezone.utc)
        except ValueError:
            continue
    return None


def _process_article(conn, msg: dict) -> None:
    data = msg.get("data", msg)  # handle both wrapped {"data":{}} and flat format
    source_url = data.get("source_url", "")
    if not source_url:
        return

    article_id = hashlib.md5(source_url.encode()).hexdigest()
    title = data.get("title") or ""
    content = data.get("content") or ""
    published_at = _parse_published_at(data.get("published_at") or data.get("publish_date") or data.get("published_date") or "")
    source_platform = data.get("source_platform") or msg.get("source_platform", "unknown")

    # Support both flat entity_techs/entity_orgs/entity_locs and nested entities.tech/org/loc
    entities = data.get("entities", {}) or {}
    techs = data.get("entity_techs") or entities.get("tech") or entities.get("TECH") or []
    orgs = data.get("entity_orgs") or entities.get("org") or entities.get("ORG") or []
    locs = data.get("entity_locs") or entities.get("loc") or entities.get("LOC") or []

    chash = content_hash(title, content)
    quality = _quality_score(title, content)

    # Kiểm tra near-duplicate trước khi insert
    dup_of = check_content_duplicate(conn, chash, article_id)
    is_dup = dup_of is not None

    with conn.cursor() as cur:
        cur.execute(
            """INSERT INTO dp_processed_articles
               (id, source_url, source_platform, title, content, published_at,
                entity_techs, entity_orgs, entity_locs,
                content_hash, is_duplicate, duplicate_of, quality_score, status)
               VALUES (%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,'processed')
               ON CONFLICT (source_url) DO NOTHING""",
            (
                article_id, source_url, source_platform,
                title[:1000] if title else None,
                content,
                published_at,
                techs, orgs, locs,
                chash, is_dup, dup_of, quality,
            ),
        )
    conn.commit()

    if is_dup:
        logger.info("Silver: DUPLICATE article {} (orig={})", article_id[:8], dup_of[:8])
    else:
        logger.info("Silver: article {} quality={} techs={} orgs={}", article_id[:8], quality, len(techs), len(orgs))


def _process_job(conn, msg: dict) -> None:
    data = msg.get("data", msg)  # handle both wrapped {"data":{}} and flat format
    job = data.get("job", data) or {}
    source_url = job.get("source_url", "")
    if not source_url:
        return

    job_id = hashlib.md5(source_url.encode()).hexdigest()
    # kafka_producer sends "job_title"; fallback to "title" for other formats
    title = job.get("job_title") or job.get("title") or ""
    desc = job.get("description") or job.get("content") or ""
    req = job.get("requirement") or ""

    # kafka_producer sends flat fields; also support nested "company" object
    company_obj = data.get("company", {}) or {}
    company_name = job.get("company_name") or company_obj.get("name") or ""
    company_location = job.get("location") or company_obj.get("location") or ""
    skills = job.get("skills") or data.get("skills") or []
    techs = job.get("technologies") or data.get("technologies") or data.get("entity_techs") or []

    chash = content_hash(title, f"{desc} {req}")
    quality = _quality_score(title, f"{desc} {req}")
    is_dup = check_job_duplicate(conn, chash, job_id)

    with conn.cursor() as cur:
        cur.execute(
            """INSERT INTO dp_processed_jobs
               (id, source_url, source_platform, job_title, company_name,
                company_location, salary, description, requirement, benefit,
                skills, technologies, content_hash, is_duplicate, quality_score, status)
               VALUES (%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,'processed')
               ON CONFLICT (source_url) DO NOTHING""",
            (
                job_id, source_url, job.get("source_platform") or msg.get("source_platform", "unknown"),
                title[:500] if title else None,
                company_name[:300] if company_name else None,
                company_location[:200] if company_location else None,
                job.get("salary", "")[:200] if job.get("salary") else None,
                desc, req,
                job.get("benefit") or "",
                skills, techs,
                chash, is_dup, quality,
            ),
        )
    conn.commit()
    logger.debug("Silver: job {} quality={}", job_id[:8], quality)


def _wait_for_kafka(servers: str, retries: int = 10, delay: int = 5) -> KafkaConsumer:
    for attempt in range(1, retries + 1):
        try:
            consumer = KafkaConsumer(
                *TOPICS,
                bootstrap_servers=servers.split(","),
                auto_offset_reset="earliest",
                enable_auto_commit=False,
                group_id=GROUP_ID,
                value_deserializer=lambda m: json.loads(m.decode("utf-8")),
                consumer_timeout_ms=1000,
            )
            logger.info("Silver Processor: connected to Kafka (attempt {})", attempt)
            return consumer
        except NoBrokersAvailable:
            logger.warning("Silver Processor: Kafka not ready, retry {}/{}", attempt, retries)
            time.sleep(delay)
    raise RuntimeError("Silver Processor: could not connect to Kafka")


def run(settings: Settings) -> None:
    logger.info("Silver Processor starting...")

    pg_conn = get_pg_conn(settings)
    consumer = _wait_for_kafka(settings.kafka_bootstrap_servers)

    logger.info("Silver Processor: listening on {}", TOPICS)

    while True:
        try:
            records = consumer.poll(timeout_ms=2000)
            for tp, messages in records.items():
                for record in messages:
                    try:
                        msg = record.value
                        if record.topic in ("raw_articles", "extracted_articles"):
                            _process_article(pg_conn, msg)
                        else:
                            _process_job(pg_conn, msg)
                        consumer.commit()
                    except Exception:
                        logger.exception("Silver: failed offset={}", record.offset)

        except Exception:
            logger.exception("Silver Processor: poll error, reconnecting in 10s")
            time.sleep(10)
            try:
                pg_conn = get_pg_conn(settings)
            except Exception:
                logger.exception("Silver Processor: Postgres reconnect failed")
