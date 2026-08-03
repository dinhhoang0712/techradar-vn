from __future__ import annotations

from collections.abc import AsyncIterator

import anthropic

from llm_gateway._retry import is_retryable_error
from llm_gateway.exceptions import LLMProviderError
from llm_gateway.types import GenerationConfig, LLMResponse, Message, TokenUsage

_DEFAULT_MAX_TOKENS = 4096


class ClaudeProvider:
    """Claude không nhận role "system" trong messages — tách ra làm tham số system= riêng."""

    name = "claude"

    def __init__(self, api_key: str, model: str, *, max_tokens: int = _DEFAULT_MAX_TOKENS):
        self.model = model
        self.max_tokens = max_tokens
        self._client = anthropic.AsyncAnthropic(api_key=api_key)

    @staticmethod
    def _split(messages: list[Message]) -> tuple[str | None, list[dict]]:
        system = "\n\n".join(m.content for m in messages if m.role == "system") or None
        turns = [{"role": m.role, "content": m.content} for m in messages if m.role != "system"]
        return system, turns

    def _kwargs(self, messages: list[Message]) -> dict:
        system, turns = self._split(messages)
        kwargs: dict = {"model": self.model, "max_tokens": self.max_tokens, "messages": turns}
        if system:
            kwargs["system"] = system
        return kwargs

    async def chat(self, messages: list[Message], config: GenerationConfig | None = None) -> LLMResponse:
        # config bị bỏ qua có chủ đích: model Claude mới không nhận temperature (400), và
        # không có json_mode kiểu response_format như OpenAI/Gemini — muốn ép JSON thì dùng
        # output_config.format (structured outputs), chưa cần cho phạm vi hiện tại.
        try:
            response = await self._client.messages.create(**self._kwargs(messages))
        except Exception as e:
            raise LLMProviderError(self.name, str(e), retryable=is_retryable_error(e)) from e

        if response.stop_reason == "refusal":
            raise LLMProviderError(self.name, "model từ chối trả lời (refusal)", retryable=False)

        text = next((b.text for b in response.content if b.type == "text"), "")
        usage = TokenUsage(input_tokens=response.usage.input_tokens, output_tokens=response.usage.output_tokens)
        return LLMResponse(text=text, usage=usage, provider=self.name, model=self.model)

    async def chat_stream(
        self, messages: list[Message], config: GenerationConfig | None = None
    ) -> AsyncIterator[str | TokenUsage]:
        try:
            async with self._client.messages.stream(**self._kwargs(messages)) as stream:
                async for text in stream.text_stream:
                    yield text
                final = await stream.get_final_message()
        except Exception as e:
            raise LLMProviderError(self.name, str(e), retryable=is_retryable_error(e)) from e

        if final.stop_reason == "refusal":
            raise LLMProviderError(self.name, "model từ chối trả lời (refusal)", retryable=False)

        yield TokenUsage(input_tokens=final.usage.input_tokens, output_tokens=final.usage.output_tokens)
