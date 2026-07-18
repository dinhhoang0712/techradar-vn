from functools import lru_cache
from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    model_config = SettingsConfigDict(
        env_file=".env",
        env_file_encoding="utf-8",
        extra="ignore",
        case_sensitive=False,
    )

    # Kafka
    kafka_bootstrap_servers: str = "localhost:9092"

    # MinIO (S3-compatible Bronze/Silver storage)
    minio_endpoint: str = "localhost:9000"
    minio_access_key: str = "minioadmin"
    minio_secret_key: str = "minioadmin123"
    minio_secure: bool = False
    bronze_bucket: str = "techradar-bronze"

    # PostgreSQL (Silver catalog + Gold analytics)
    postgres_dsn: str = "postgresql://postgres:postgres@localhost:5432/techradar"

    # Neo4j (Gold Knowledge Graph)
    neo4j_uri: str = "bolt://localhost:7687"
    neo4j_username: str = "neo4j"
    neo4j_password: str = "password"

    # ai-rag-core (embed trigger)
    rag_base_url: str = "http://localhost:8000"
    embed_secret: str = "changeme"
    internal_api_token: str = "techradar-internal-secret"

    # ml-clustering (retrain trigger)
    ml_clustering_base_url: str = "http://localhost:8001"

    # Tech Dedup (LLM judge cho case chưa có trong dp_tech_alias_map)
    openai_api_key: str = ""
    gemini_api_key: str = ""
    tech_dedup_llm_provider: str = "gemini"  # "gemini" | "openai"
    tech_dedup_openai_model: str = "gpt-4o-mini"
    tech_dedup_gemini_model: str = "gemini-2.5-flash"

    # Scheduler cron (hour, minute in Asia/Ho_Chi_Minh)
    article_sync_hour: int = 2
    article_sync_minute: int = 0
    job_sync_hour: int = 2
    job_sync_minute: int = 30
    gold_etl_hour: int = 3
    gold_etl_minute: int = 0
    embed_trigger_hour: int = 4
    embed_trigger_minute: int = 0
    neo4j_enricher_hour: int = 5
    neo4j_enricher_minute: int = 0
    tech_dedup_hour: int = 5
    tech_dedup_minute: int = 30
    # Clustering retrain: chạy sau neo4j_enricher (6 AM), mỗi tuần Chủ nhật
    clustering_retrain_hour: int = 6
    clustering_retrain_minute: int = 0
    clustering_retrain_day_of_week: str = "sun"
    # job_retrain_clustering poll GET /pipeline/status mỗi khoảng này (giây) cho tới khi
    # pipeline xong hoặc hết clustering_retrain_max_wait_s, để biết kết quả THẬT (không
    # chỉ "đã trigger thành công") trước khi ghi dp_pipeline_runs.
    clustering_retrain_poll_interval_s: int = 30
    clustering_retrain_max_wait_s: int = 7200

    # Dev: chạy tất cả jobs ngay khi start (seed initial data)
    run_jobs_on_start: bool = False

    # Redis (kênh admin trigger job thủ công — xem common/job_trigger_listener.py)
    redis_url: str = "redis://localhost:6379"


@lru_cache(maxsize=1)
def get_settings() -> Settings:
    return Settings()
