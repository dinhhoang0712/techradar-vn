import uuid
from datetime import datetime
from pydantic import BaseModel, Field


# ── Chat request / response ───────────────────────────────────────────────────

class ChatRequest(BaseModel):
    query:      str       = Field(..., min_length=1, max_length=2000)
    session_id: uuid.UUID | None = Field(None, description="UUID phiên hội thoại, None để tạo mới")
    user_id:    uuid.UUID | None = Field(None, description="UUID user đã đăng nhập, None nếu ẩn danh")


class SourceItem(BaseModel):
    title:          str | None
    published_date: str | None
    source:         str | None
    rerank_score:   float | None


class ChatResponse(BaseModel):
    answer:     str
    session_id: uuid.UUID
    sources:    list[SourceItem] = []
    entities:   list[str]        = []
    job_titles: list[str]        = []
    query:      str


class ChatMessageItem(BaseModel):
    id:      uuid.UUID
    role:    str
    content: str


# ── Session ───────────────────────────────────────────────────────────────────

class SessionCreate(BaseModel):
    user_id: uuid.UUID
    title:   str | None = None


class SessionResponse(BaseModel):
    id:         uuid.UUID
    user_id:    uuid.UUID
    title:      str | None
    created_at: datetime

    model_config = {"from_attributes": True}


# ── Health ────────────────────────────────────────────────────────────────────

class HealthResponse(BaseModel):
    status:  str
    neo4j:   bool
    version: str = "1.0.0"


# ── Internal: LLM compare summary (gọi từ Spring gateway) ─────────────────────

class LlmSummaryRequest(BaseModel):
    tech1: str
    tech2: str
    growth_rate_1:   float | None = None
    growth_rate_2:   float | None = None
    job_count_1:     int | None = None
    job_count_2:     int | None = None
    article_count_1: int | None = None
    article_count_2: int | None = None


class LlmSummaryResponse(BaseModel):
    summary: str
