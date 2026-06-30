"""Shared database connection helpers."""
import psycopg2
import psycopg2.extras
from neo4j import GraphDatabase
from minio import Minio
from loguru import logger

from config import Settings


def get_pg_conn(settings: Settings):
    return psycopg2.connect(settings.postgres_dsn, cursor_factory=psycopg2.extras.RealDictCursor)


def get_neo4j_driver(settings: Settings):
    return GraphDatabase.driver(
        settings.neo4j_uri,
        auth=(settings.neo4j_username, settings.neo4j_password),
    )


def get_minio_client(settings: Settings) -> Minio:
    return Minio(
        settings.minio_endpoint,
        access_key=settings.minio_access_key,
        secret_key=settings.minio_secret_key,
        secure=settings.minio_secure,
    )


def ensure_bronze_bucket(client: Minio, bucket: str) -> None:
    if not client.bucket_exists(bucket):
        client.make_bucket(bucket)
        logger.info("Created MinIO bucket: {}", bucket)


def log_pipeline_run(conn, job_name: str, status: str,
                     rows_affected: int = None, error_msg: str = None,
                     run_id: int = None) -> int:
    with conn.cursor() as cur:
        if run_id is None:
            cur.execute(
                """INSERT INTO dp_pipeline_runs (job_name, status, rows_affected, error_msg)
                   VALUES (%s, %s, %s, %s) RETURNING id""",
                (job_name, status, rows_affected, error_msg),
            )
            run_id = cur.fetchone()["id"]
        else:
            cur.execute(
                """UPDATE dp_pipeline_runs
                   SET status=%s, rows_affected=%s, error_msg=%s, finished_at=now()
                   WHERE id=%s""",
                (status, rows_affected, error_msg, run_id),
            )
        conn.commit()
    return run_id
