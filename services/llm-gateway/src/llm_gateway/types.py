"""Kiểu dữ liệu dùng chung giữa gateway và mọi provider adapter."""

from __future__ import annotations

from dataclasses import dataclass
from typing import Literal

Role = Literal["system", "user", "assistant"]


@dataclass(frozen=True, slots=True)
class Message:
    role: Role
    content: str


@dataclass(frozen=True, slots=True)
class GenerationConfig:
    """Tham số sinh text — optional, provider nào không hỗ trợ field nào thì tự bỏ qua
    field đó (ví dụ Claude không có json_mode kiểu OpenAI/Gemini)."""

    temperature: float | None = None
    json_mode: bool = False
    """True = yêu cầu provider trả JSON thuần (OpenAI response_format, Gemini response_mime_type)."""


@dataclass(frozen=True, slots=True)
class TokenUsage:
    """Số token đã dùng cho 1 lần gọi — chuẩn hoá từ format riêng của mỗi provider."""

    input_tokens: int
    output_tokens: int

    @property
    def total_tokens(self) -> int:
        return self.input_tokens + self.output_tokens


@dataclass(frozen=True, slots=True)
class LLMResponse:
    text: str
    usage: TokenUsage
    provider: str
    model: str


@dataclass(frozen=True, slots=True)
class UsageRecord:
    """Bản ghi 1 lần gọi LLM, đủ thông tin để tính tiền và log billing."""

    provider: str
    model: str
    usage: TokenUsage
    cost_usd: float
    fallback_from: str | None = None
    """Tên provider đã thất bại trước khi rơi xuống provider này, nếu có."""
