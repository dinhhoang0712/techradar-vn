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

    # Scheduler cron (hour, minute in Asia/Ho_Chi_Minh)
    gold_etl_hour: int = 3
    gold_etl_minute: int = 0
    embed_trigger_hour: int = 4
    embed_trigger_minute: int = 0
    neo4j_enricher_hour: int = 5
    neo4j_enricher_minute: int = 0
    # Clustering retrain: chạy sau neo4j_enricher (6 AM), mỗi tuần Chủ nhật
    clustering_retrain_hour: int = 6
    clustering_retrain_minute: int = 0
    clustering_retrain_day_of_week: str = "sun"

    # Dev: chạy tất cả jobs ngay khi start (seed initial data)
    run_jobs_on_start: bool = False


@lru_cache(maxsize=1)
def get_settings() -> Settings:
    return Settings()
