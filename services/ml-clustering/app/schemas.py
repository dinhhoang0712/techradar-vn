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


class ClusterDetail(ClusterSummary):
    description: str
    coherence_reason: str | None
    outliers: list[str]
    members: list[str]          # tên tech trong cluster


# ---------------------------------------------------------------------------
# Tech schemas
# ---------------------------------------------------------------------------

class TechClusterResult(BaseModel):
    tech_name: str
    tech_id: str | None
    cluster_id: int | None      # None → noise (label=-1) hoặc không tìm thấy
    label: str | None
    label_en: str | None
    domain: str | None
    found: bool                 # False nếu tech_name không có trong DB snapshot


# ---------------------------------------------------------------------------
# Batch predict
# ---------------------------------------------------------------------------

class BatchPredictRequest(BaseModel):
    tech_names: list[str]


class BatchPredictResponse(BaseModel):
    results: list[TechClusterResult]
    n_found: int
    n_not_found: int
    snapshot_tag: str
