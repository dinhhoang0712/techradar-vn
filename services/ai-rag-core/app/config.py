from functools import lru_cache
from pathlib import Path
from pydantic_settings import BaseSettings, SettingsConfigDict

# .env nằm ở project root (2 cấp trên file này)
_ENV_FILE = Path(__file__).parent.parent.parent.parent / ".env"


class Settings(BaseSettings):
    # Neo4j AuraDB (cloud)
    neo4j_uri: str
    neo4j_username: str = "neo4j"
    neo4j_password: str

    # Neo4j Local (dev)
    neo4j_local_uri: str = "bolt://localhost:7687"
    neo4j_local_username: str = "neo4j"
    neo4j_local_password: str = "localpassword"

    # Chọn local hay AuraDB — đặt USE_LOCAL_NEO4J=true trong .env khi dev
    use_local_neo4j: bool = False

    # LLM
    gemini_api_key: str = ""
    openai_api_key: str = ""
    llm_provider: str = "openai"   # "openai" | "gemini"

    # PostgreSQL
    postgres_host: str = "localhost"
    postgres_port: int = 5432
    postgres_db: str = "techpulse"
    postgres_user: str = "postgres"
    postgres_password: str = "postgres"

    # Redis
    redis_url: str = "redis://localhost:6379"

    # Model
    embedding_model: str = "intfloat/multilingual-e5-base"
    reranker_backend: str = "onnx"  # "cross_encoder" | "onnx"
    reranker_model: str = "woxpas-ai/bge-reranker-v2-m3-onnx"
    reranker_onnx_file: str = "onnx/model_quantized.onnx"
    llm_model: str = "gpt-4o-mini"
    embedding_dim: int = 768
    model_warmup: str = "blocking"  # "none" | "background" | "blocking"
    warmup_ner_model: bool = True

    # Neo4j vector index name
    neo4j_vector_index: str = "article_embedding_index"

    # Secret để crawler gọi trigger embed (đặt giá trị ngẫu nhiên trong .env)
    embed_secret: str = "changeme"

    # Shared secret để CHỈ Spring gateway gọi được /chat, /chat/stream, /internal/ai/*.
    # Để TRỐNG (mặc định) = tắt kiểm tra (tiện dev/test). Đặt giá trị trong .env để bật.
    internal_api_token: str = ""

    # CORS: danh sách origin (phân tách bằng dấu phẩy). "*" = mở (chỉ nên dùng khi đứng sau gateway).
    cors_origins: str = "*"

    # Evaluation — RAGAS-style LLM judge (tắt mặc định để không tăng latency)
    eval_enabled: bool = False

    # SQL Analytics
    sql_analytics_months: int = 6

    # Recommendation
    recommend_top_k: int = 10

    # Forecast
    forecast_min_data_points: int = 3

    model_config = SettingsConfigDict(
        env_file=str(_ENV_FILE),
        env_file_encoding="utf-8",
        extra="ignore",
    )

    @property
    def active_neo4j_uri(self) -> str:
        return self.neo4j_local_uri if self.use_local_neo4j else self.neo4j_uri

    @property
    def active_neo4j_username(self) -> str:
        return self.neo4j_local_username if self.use_local_neo4j else self.neo4j_username

    @property
    def active_neo4j_password(self) -> str:
        return self.neo4j_local_password if self.use_local_neo4j else self.neo4j_password

    @property
    def postgres_dsn(self) -> str:
        # asyncpg dùng `ssl=require` qua connect_args; query string `?sslmode=...` không có hiệu lực.
        # Ta để URL sạch và bật SSL khi host không phải local.
        return (
            f"postgresql+asyncpg://{self.postgres_user}:{self.postgres_password}"
            f"@{self.postgres_host}:{self.postgres_port}/{self.postgres_db}"
        )

    @property
    def postgres_requires_ssl(self) -> bool:
        host = (self.postgres_host or "").lower()
        return host not in {"localhost", "127.0.0.1", "postgres", "techpulse-postgres"}


@lru_cache
def get_settings() -> Settings:
    return Settings()
