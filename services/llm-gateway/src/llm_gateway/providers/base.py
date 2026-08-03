"""Cái "khuôn" mọi provider adapter phải theo.

Thêm provider mới = viết 1 class implement đúng Protocol này, không sửa gì ở
gateway.py hay ở phía service gọi gateway.
"""

from __future__ import annotations

from collections.abc import AsyncIterator
from typing import Protocol, runtime_checkable

from llm_gateway.types import GenerationConfig, LLMResponse, Message, TokenUsage


@runtime_checkable
class LLMProvider(Protocol):
    name: str
    model: str

    async def chat(self, messages: list[Message], config: GenerationConfig | None = None) -> LLMResponse:
        """Gọi API thật, trả về text + usage đã chuẩn hoá. config=None -> dùng mặc định của provider
        (không set temperature, không ép JSON mode)."""
        ...

    async def chat_stream(
        self, messages: list[Message], config: GenerationConfig | None = None
    ) -> AsyncIterator[str | TokenUsage]:
        """Stream text theo chunk (str); chunk cuối cùng là TokenUsage nếu provider trả được usage.

        Gateway phân biệt bằng isinstance() — text chunk là str, usage là TokenUsage.
        Nếu provider không trả usage cuối stream, đơn giản là không yield TokenUsage.
        """
        ...
