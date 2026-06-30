"""
Bronze Writer — Kafka consumer group: bronze-writer
Subscribes: raw_articles, raw_jobs
Action: Ghi raw message vào MinIO dưới dạng gzip JSON, update catalog PostgreSQL.

Dùng group-id riêng (bronze-writer) nên KHÔNG ảnh hưởng đến Spring Boot consumer.
Raw data là IMMUTABLE — không bao giờ xoá hoặc ghi đè.
"""
import gzip
import hashlib
import io
import json
import time
from datetime import datetime, timezone

import psycopg2
import psycopg2.extras
from kafka import KafkaConsumer
from kafka.errors import NoBrokersAvailable
from loguru import logger
from minio import Minio

from common.db import ensure_bronze_bucket, get_minio_client, get_pg_conn
from config import Settings

TOPICS = ["raw_articles", "raw_jobs"]
GROUP_ID = "bronze-writer"
MINIO_BUCKET = "techradar-bronze"


def _build_object_path(topic: str, source: str, now: datetime, file_id: str) -> str:
    content_type = "articles" if topic == "raw_articles" else "jobs"
    return (
        f"raw/{content_type}/{source.lower()}/"
        f"year={now.year}/month={now.month:02d}/day={now.day:02d}/"
        f"{file_id}_{now.strftime('%Y%m%dT%H%M%SZ')}.json.gz"
    )


def _write_to_minio(client: Minio, path: str, raw_bytes: bytes) -> int:
    compressed = gzip.compress(raw_bytes)
    client.put_object(
        MINIO_BUCKET,
        path,
        data=io.BytesIO(compressed),
        length=len(compressed),
        content_type="application/gzip",
    )
    return len(compressed)


def _update_catalog(conn, file_id: str, source_url: str, source_platform: str,
                    content_type: str, minio_path: str, file_size: int,
                    kafka_topic: str, kafka_offset: int) -> None:
    with conn.cursor() as cur:
        cur.execute(
            """INSERT INTO dp_bronze_catalog
               (id, source_url, source_platform, content_type,
                minio_path, file_size_bytes, kafka_topic, kafka_offset)
               VALUES (%s, %s, %s, %s, %s, %s, %s, %s)
               ON CONFLICT (id) DO NOTHING""",
            (file_id, source_url, source_platform, content_type,
             minio_path, file_size, kafka_topic, kafka_offset),
        )
    conn.commit()


def _extract_source_url(msg: dict, topic: str) -> str:
    data = msg.get("data", msg)  # handle both wrapped {"data":{}} and flat format
    if topic == "raw_articles":
        return data.get("source_url", "")
    job = data.get("job", data)
    return job.get("source_url", "")


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
            logger.info("Bronze Writer: connected to Kafka (attempt {})", attempt)
            return consumer
        except NoBrokersAvailable:
            logger.warning("Bronze Writer: Kafka not ready, retrying in {}s ({}/{})", delay, attempt, retries)
            time.sleep(delay)
    raise RuntimeError("Bronze Writer: could not connect to Kafka after retries")


def run(settings: Settings) -> None:
    logger.info("Bronze Writer starting...")

    minio_client = get_minio_client(settings)
    ensure_bronze_bucket(minio_client, MINIO_BUCKET)

    pg_conn = get_pg_conn(settings)
    consumer = _wait_for_kafka(settings.kafka_bootstrap_servers)

    logger.info("Bronze Writer: listening on topics {}", TOPICS)

    while True:
        try:
            records = consumer.poll(timeout_ms=2000)
            for tp, messages in records.items():
                for record in messages:
                    try:
                        msg = record.value
                        now = datetime.now(timezone.utc)
                        source_platform = msg.get("source_platform", "unknown")
                        source_url = _extract_source_url(msg, record.topic)

                        # Content ID = MD5 of source_url (idempotent)
                        file_id = hashlib.md5(source_url.encode()).hexdigest()
                        content_type = "article" if record.topic == "raw_articles" else "job"
                        minio_path = _build_object_path(record.topic, source_platform, now, file_id)

                        raw_bytes = json.dumps(msg, ensure_ascii=False).encode("utf-8")
                        file_size = _write_to_minio(minio_client, minio_path, raw_bytes)

                        _update_catalog(
                            pg_conn, file_id, source_url, source_platform,
                            content_type, f"s3://{MINIO_BUCKET}/{minio_path}",
                            file_size, record.topic, record.offset,
                        )

                        consumer.commit()
                        logger.info("Bronze: {} [{}] → {}", source_platform, content_type, minio_path)

                    except Exception:
                        logger.exception("Bronze: failed to process message offset={}", record.offset)

        except Exception:
            logger.exception("Bronze Writer: poll error, reconnecting in 10s")
            time.sleep(10)
            try:
                pg_conn = get_pg_conn(settings)
            except Exception:
                logger.exception("Bronze Writer: failed to reconnect to Postgres")
