from __future__ import annotations

import uuid
from datetime import datetime
from sqlalchemy import String, Text, Integer, ForeignKey, DateTime, func
from sqlalchemy.dialects.postgresql import UUID
from sqlalchemy.orm import Mapped, mapped_column, relationship

from app.db.postgres_client import Base


class ChatSession(Base):
    __tablename__ = "chat_session"

    id:         Mapped[uuid.UUID]     = mapped_column(UUID(as_uuid=True), primary_key=True, default=uuid.uuid4)
    user_id:    Mapped[uuid.UUID]     = mapped_column(UUID(as_uuid=True), ForeignKey("users.id"), nullable=False)
    title:      Mapped[str | None]    = mapped_column(String(500))
    created_at: Mapped[datetime]      = mapped_column(DateTime(timezone=True), server_default=func.now())

    messages: Mapped[list[ChatMessage]] = relationship("ChatMessage", back_populates="session", order_by="ChatMessage.created_at")
    user:     Mapped["User"]            = relationship("User", back_populates="sessions")


class ChatMessage(Base):
    __tablename__ = "chat_message"

    id:                Mapped[uuid.UUID]  = mapped_column(UUID(as_uuid=True), primary_key=True, default=uuid.uuid4)
    session_id:        Mapped[uuid.UUID]  = mapped_column(UUID(as_uuid=True), ForeignKey("chat_session.id"), nullable=False)
    role:              Mapped[str]        = mapped_column(String(20), nullable=False)
    content:           Mapped[str]        = mapped_column(Text, nullable=False)
    prompt_tokens:     Mapped[int | None] = mapped_column(Integer)
    completion_tokens: Mapped[int | None] = mapped_column(Integer)
    finish_reason:     Mapped[str | None] = mapped_column(String(50))
    created_at:        Mapped[datetime]   = mapped_column(DateTime(timezone=True), server_default=func.now())

    session: Mapped[ChatSession] = relationship("ChatSession", back_populates="messages")
