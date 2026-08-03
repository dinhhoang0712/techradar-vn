from __future__ import annotations

import uuid
from datetime import datetime

from sqlalchemy import DateTime, Integer, Numeric, String, func
from sqlalchemy.dialects.postgresql import UUID
from sqlalchemy.orm import Mapped, mapped_column

from app.db.postgres_client import Base


class LLMUsageLog(Base):
    """Mirror của bảng llm_usage_log (schema owned bởi Flyway — xem V33__llm_usage_log.sql).

    1 dòng = 1 lần llm-gateway gọi thành công 1 provider. Dùng để tính chi phí LLM theo
    provider/model/thời gian (bước Cost Tracking / Billing của llm-gateway).
    """

    __tablename__ = "llm_usage_log"

    id: Mapped[uuid.UUID] = mapped_column(UUID(as_uuid=True), primary_key=True, default=uuid.uuid4)
    service: Mapped[str] = mapped_column(String(50), nullable=False)
    provider: Mapped[str] = mapped_column(String(50), nullable=False)
    model: Mapped[str] = mapped_column(String(100), nullable=False)
    input_tokens: Mapped[int] = mapped_column(Integer, nullable=False)
    output_tokens: Mapped[int] = mapped_column(Integer, nullable=False)
    cost_usd: Mapped[float] = mapped_column(Numeric(12, 6), nullable=False)
    fallback_from: Mapped[str | None] = mapped_column(String(50))
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), server_default=func.now())
