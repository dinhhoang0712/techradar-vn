"""
Pydantic schemas cho ml-clustering API.
"""

from __future__ import annotations

from pydantic import BaseModel

# ---------------------------------------------------------------------------
# Cluster schemas
# ---------------------------------------------------------------------------


class ClusterSummary(BaseModel):
    cluster_id: int
    label: str
    label_en: str
    domain: str
    confidence: float
    is_coherent: bool
    n_members: int
    overridden: bool = False  # True nếu admin đã ghi đè nhãn AI-generated


class ClusterDetail(ClusterSummary):
    description: str
    coherence_reason: str | None
    outliers: list[str]
    members: list[str]  # tên tech trong cluster
    overridden_by: str | None = None
    overridden_at: str | None = None


class ClusterLabelOverrideRequest(BaseModel):
    """Admin ghi đè 1 hoặc nhiều trường nhãn AI-generated. Ít nhất 1 trường phải có giá trị."""

    label: str | None = None
    label_en: str | None = None
    description: str | None = None
    domain: str | None = None


# ---------------------------------------------------------------------------
# Tech schemas
# ---------------------------------------------------------------------------


class NearClusterEntry(BaseModel):
    cluster_id: int
    score: float
    label: str | None
    label_en: str | None


class TechClusterResult(BaseModel):
    tech_name: str
    tech_id: str | None
    cluster_id: int | None  # None → noise (label=-1) hoặc không tìm thấy
    label: str | None
    label_en: str | None
    domain: str | None
    found: bool  # False nếu tech_name không có trong DB snapshot
    membership_probability: float | None = None  # độ tin cậy gán cụm (0..1) — chỉ có với hdbscan
    outlier_score: float | None = None  # GLOSH outlier score — chỉ có với hdbscan
    near_clusters: list[NearClusterEntry] = []  # cụm "gần" khác, hữu ích với noise/biên
    # Provisional: tech chưa có trong snapshot (thật sự mới, hoặc chưa retrain kịp) được
    # gán TẠM vào cluster của tech đã biết có tên giống nhất (difflib, xem
    # AppStore.find_nearest_known_tech) — thay vì bắt caller đợi tới lần retrain kế tiếp.
    # found vẫn là False (không phải kết quả HDBSCAN thật) — luôn kiểm tra `provisional`
    # trước khi coi cluster_id là đáng tin cậy.
    provisional: bool = False
    matched_via: str | None = None  # tên tech đã biết được dùng để suy ra cluster tạm
    match_score: float | None = None  # difflib ratio() của matched_via, 0..1


# ---------------------------------------------------------------------------
# Batch predict
# ---------------------------------------------------------------------------


class BatchPredictRequest(BaseModel):
    tech_names: list[str]


class BatchPredictResponse(BaseModel):
    results: list[TechClusterResult]
    n_found: int
    n_provisional: int = 0  # found=False nhưng gán tạm được qua find_nearest_known_tech
    n_not_found: int  # thật sự không tìm được gì, kể cả provisional
    snapshot_tag: str


# ---------------------------------------------------------------------------
# Pipeline run history (MLflow) — theo dõi chất lượng model qua các lần train
# ---------------------------------------------------------------------------


class PipelineRunSummary(BaseModel):
    run_id: str
    snapshot_tag: str | None = None
    status: str
    started_at: str | None = None
    finished_at: str | None = None
    duration_s: float | None = None
    algorithm: str | None = None
    metrics: dict[str, float] = {}
