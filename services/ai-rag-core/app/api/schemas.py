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


# ── Recommendation ────────────────────────────────────────────────────────────

class RecommendRequest(BaseModel):
    user_id:      uuid.UUID | None = Field(None, description="UUID user đã đăng nhập")
    current_techs: list[str]       = Field([], description="Override nếu không có user profile")
    limit:        int              = Field(10, ge=1, le=20)


class RecommendItem(BaseModel):
    tech_name:    str
    reason:       str
    ring:         str | None   = None   # Adopt / Trial / Assess / Hold
    growth_rate:  float | None = None
    co_occurrence: int         = 0
    confidence:   float        = 0.0


class RecommendResponse(BaseModel):
    recommendations: list[RecommendItem]
    based_on:        list[str]           # tech đã dùng để tính recommendation


# ── Forecast ──────────────────────────────────────────────────────────────────

class ForecastRequest(BaseModel):
    technology:     str
    horizon_months: int = Field(6, ge=1, le=24)


class ForecastSignal(BaseModel):
    signal: str
    value:  float | str
    weight: float


class ForecastResponse(BaseModel):
    technology:          str
    current_status:      dict                 # job_count, article_count, growth_rate
    predicted_direction: str                  # "growing" | "stable" | "declining"
    confidence:          float
    reasoning:           str
    signals:             list[ForecastSignal]
    trend_data:          list[dict]           # [{month, job_count, growth_rate}]


# ── Career Assistant ──────────────────────────────────────────────────────────

class CareerRequest(BaseModel):
    user_id:     uuid.UUID | None = None
    target_role: str | None       = None   # VD: "Senior Backend Developer"
    current_skills: list[str]     = []


class CareerStep(BaseModel):
    skill:       str
    priority:    int
    reason:      str
    job_demand:  int | None = None


class CareerResponse(BaseModel):
    target_role:   str
    current_skills: list[str]
    skill_gap:     list[CareerStep]
    roadmap:       str              # LLM-generated markdown roadmap
    estimated_months: int | None = None


# ── Summarization ─────────────────────────────────────────────────────────────

class SummarizeRequest(BaseModel):
    tech_name: str
    period:    str | None = None   # VD: "2024-Q4" hoặc None = 3 tháng gần nhất
    format:    str        = Field("paragraph", pattern="^(paragraph|bullet|structured)$")


class SummarizeResponse(BaseModel):
    tech_name:    str
    period:       str
    summary:      str
    key_points:   list[str]
    sources_used: int


# ── Report Generator ──────────────────────────────────────────────────────────

class ReportRequest(BaseModel):
    period:     str        = Field(..., description="VD: '2024-Q4', '2024-12', '2024'")
    top_n:      int        = Field(10, ge=5, le=30)
    format:     str        = Field("markdown", pattern="^(markdown|json)$")


class ReportResponse(BaseModel):
    period:     str
    report:     str         # markdown hoặc JSON string
    top_techs:  list[dict]  # [{name, growth_rate, job_count, cluster_label}]
    generated_at: str


# ── AI Agent Workflow ─────────────────────────────────────────────────────────

class AgentRequest(BaseModel):
    query:   str       = Field(..., min_length=1, max_length=2000)
    user_id: uuid.UUID | None = Field(None, description="UUID user đã đăng nhập")


class AgentStep(BaseModel):
    tool:   str
    input:  str
    output: str


class AgentResponse(BaseModel):
    answer: str
    steps:  list[AgentStep] = []
