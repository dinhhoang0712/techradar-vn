from __future__ import annotations

import uuid
from sqlalchemy import String, Text, ForeignKey
from sqlalchemy.dialects.postgresql import UUID, ARRAY
from sqlalchemy.orm import Mapped, mapped_column, relationship

from app.db.postgres_client import Base


class User(Base):
    __tablename__ = "users"

    id:            Mapped[uuid.UUID]  = mapped_column(UUID(as_uuid=True), primary_key=True, default=uuid.uuid4)
    email:         Mapped[str]        = mapped_column(String(255), unique=True, nullable=False)
    password_hash: Mapped[str]        = mapped_column(Text, nullable=False)
    full_name:     Mapped[str | None] = mapped_column(String(255))

    profile:  Mapped[UserProfile | None] = relationship("UserProfile", back_populates="user", uselist=False)
    sessions: Mapped[list]               = relationship("ChatSession", back_populates="user")


class UserProfile(Base):
    __tablename__ = "user_profile"

    user_id:      Mapped[uuid.UUID]   = mapped_column(UUID(as_uuid=True), ForeignKey("users.id"), primary_key=True)
    job_role:     Mapped[str | None]  = mapped_column(String(255))
    technologies: Mapped[list[str]]   = mapped_column(ARRAY(Text), default=list)
    location:     Mapped[str | None]  = mapped_column(String(255))
    bio:          Mapped[str | None]  = mapped_column(Text)

    user: Mapped[User] = relationship("User", back_populates="profile")
