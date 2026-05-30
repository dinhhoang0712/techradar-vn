"""
Cấu hình cho module ml-clustering.

Chiến lược:
  - Tái dùng `.env` ở project root (đã có NEO4J_URI / GEMINI_API_KEY).
  - Nạp `params.yaml` (DVC-tracked) cho hyperparameters.
  - Tách rõ secret (env) vs hyperparameter (yaml) — DVC chỉ track yaml.
"""

import functools
from pathlib import Path
from typing import Any, Literal

import yaml
from pydantic import BaseModel
from pydantic_settings import BaseSettings, SettingsConfigDict

PROJECT_ROOT: Path = Path(__file__).resolve().parents[3]
MODULE_ROOT:  Path = Path(__file__).resolve().parents[1]
DATA_DIR:     Path = MODULE_ROOT / "data"
ENV_FILE:     Path = PROJECT_ROOT / ".env"


class Settings(BaseSettings):
    """
    Secret + connection từ `.env`. KHÔNG track DVC, KHÔNG commit git.

    Yêu cầu:
      - Nạp các biến: NEO4J_URI, NEO4J_USERNAME, NEO4J_PASSWORD, GEMINI_API_KEY.
      - Hỗ trợ `USE_LOCAL_NEO4J=true` để chuyển sang Neo4j local khi dev.
      - Convert `neo4j+s://` → `neo4j+ssc://` cho AuraDB trên macOS (chiến lược đã
        dùng ở `src/ai-rag-core/app/db/neo4j_client.py`).
      - Cung cấp property `active_neo4j_uri/username/password` để code khác chỉ
        đọc 1 cặp duy nhất, không cần tự if/else.
    """

    neo4j_uri: str
    neo4j_username: str = "neo4j"
    neo4j_password: str

    neo4j_local_uri: str = "bolt://localhost:7687"
    neo4j_local_username: str = "neo4j"
    neo4j_local_password: str = "localpassword"
    use_local_neo4j: bool = False

    gemini_api_key: str = ""
    openai_api_key: str = ""

    model_config = SettingsConfigDict(
        env_file=str(ENV_FILE),
        env_file_encoding="utf-8",
        extra="ignore",
    )

    @property
    def active_neo4j_uri(self) -> str:
        """Trả về URI hoạt động (đã chuẩn hoá `neo4j+s://` → `neo4j+ssc://`)."""
        uri = self.neo4j_local_uri if self.use_local_neo4j else self.neo4j_uri
        return uri.replace("neo4j+s://", "neo4j+ssc://")

    @property
    def active_neo4j_username(self) -> str:
        return self.neo4j_local_username if self.use_local_neo4j else self.neo4j_username

    @property
    def active_neo4j_password(self) -> str:
        return self.neo4j_local_password if self.use_local_neo4j else self.neo4j_password


# ---------------------------------------------------------------------------
# Pydantic models map sang `params.yaml`. Chỉ định nghĩa schema, không load.
# ---------------------------------------------------------------------------

class SnapshotParams(BaseModel):
    tag: str
    min_tech_degree: int = 1


class FastRPParams(BaseModel):
    enabled: bool = True
    embedding_dim: int = 64
    iteration_weights: list[float] = [0.0, 1.0, 1.0, 1.0]
    normalization_strength: float = 0.0


class Node2VecParams(BaseModel):
    enabled: bool = False
    embedding_dim: int = 64
    walk_length: int = 10
    walks_per_node: int = 10


class PageRankParams(BaseModel):
    enabled: bool = True
    damping: float = 0.85
    max_iter: int = 20


class LouvainParams(BaseModel):
    enabled: bool = True
    max_levels: int = 10
    top_k_communities: int = 20


class ArticleAggParams(BaseModel):
    enabled: bool = True
    method: Literal["mean", "weighted_by_recency"] = "mean"
    min_articles_per_tech: int = 1


class ReduceDimParams(BaseModel):
    enabled: bool = True
    method: Literal["umap", "pca", "none"] = "umap"
    n_components: int = 32


class NoiseFilterParams(BaseModel):
    enabled: bool = False
    min_job_count: int = 1          # loại tech có ít hơn N job
    blocklist: list[str] = []       # tên cụ thể cần loại
    heuristic_patterns: list[str] = []  # regex pattern — match → loại


class FeatureParams(BaseModel):
    fastrp: FastRPParams
    node2vec: Node2VecParams
    pagerank: PageRankParams
    louvain: LouvainParams
    noise_filter: NoiseFilterParams = NoiseFilterParams()
    use_name_embedding: bool = False  # True = luôn encode tên tech, bỏ qua article embedding
    name_emb_pca_components: int = 0  # >0: giảm name_emb xuống N chiều bằng PCA trước khi concat
    article_embedding_aggregation: ArticleAggParams
    use_company_tfidf: bool = True
    use_job_tfidf: bool = True
    tfidf_min_df: int = 2
    tfidf_max_features: int = 500
    feature_weights: dict[str, float] = {}  # nhân vào block sau scale: {"job_tfidf": 2.0, ...}
    scaler: Literal["standard", "minmax", "robust"] = "standard"
    reduce_dim: ReduceDimParams


class DBSCANGrid(BaseModel):
    eps_grid: list[float]
    min_samples_grid: list[int]
    metric: str = "cosine"


class HDBSCANGrid(BaseModel):
    min_cluster_size_grid: list[int]
    min_samples_grid: list[int | None]
    cluster_selection_method: Literal["eom", "leaf"] = "eom"


class KMeansGrid(BaseModel):
    n_clusters_grid: list[int]
    n_init: int = 10
    random_state: int = 42


class SelectionParams(BaseModel):
    primary_metric: Literal["silhouette", "davies_bouldin", "calinski_harabasz"]
    require_min_clusters: int = 5
    require_max_clusters: int = 9999  # không giới hạn mặc định
    require_max_noise_ratio: float = 0.6


class ClusteringParams(BaseModel):
    algorithm: Literal["dbscan", "hdbscan", "kmeans"]
    dbscan: DBSCANGrid
    hdbscan: HDBSCANGrid
    kmeans: KMeansGrid
    near_cluster_threshold: float = 0.70
    selection: SelectionParams


class MLflowParams(BaseModel):
    experiment_name: str
    tracking_uri: str
    registry_model_name: str


class LabelingParams(BaseModel):
    provider: str = "gemini"           # "gemini" | "openai"
    gemini_model: str = "gemini-2.5-flash"
    openai_model: str = "gpt-4o-mini"
    max_members_in_prompt: int = 25
    temperature: float = 0.2
    cache_dir: str = "data/labels/.llm_cache"


class WritebackParams(BaseModel):
    enabled: bool = False
    apoc_batch_size: int = 500
    clean_before_write: bool = False


class Params(BaseModel):
    """Schema gộp cho `params.yaml`."""
    snapshot: SnapshotParams
    features: FeatureParams
    clustering: ClusteringParams
    mlflow: MLflowParams
    labeling: LabelingParams
    writeback: WritebackParams


# ---------------------------------------------------------------------------
# Loaders
# ---------------------------------------------------------------------------

@functools.lru_cache(maxsize=1)
def get_settings() -> Settings:
    return Settings()


def load_params(path: str | Path = MODULE_ROOT / "params.yaml") -> Params:
    with open(path, encoding="utf-8") as f:
        raw = yaml.safe_load(f)
    return Params.model_validate(raw)


def snapshot_dir(tag: str) -> Path:
    p = DATA_DIR / "raw" / f"snapshot_{tag}"
    p.mkdir(parents=True, exist_ok=True)
    return p


def features_dir(tag: str) -> Path:
    return DATA_DIR / "features" / tag


def labels_dir(tag: str) -> Path:
    return DATA_DIR / "labels" / tag


def models_dir(tag: str) -> Path:
    return DATA_DIR / "models" / tag


def metrics_dir(tag: str) -> Path:
    return DATA_DIR / "metrics" / tag
