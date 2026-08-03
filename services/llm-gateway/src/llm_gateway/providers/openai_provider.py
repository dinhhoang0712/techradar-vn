from __future__ import annotations

from collections.abc import AsyncIterator

from openai import AsyncOpenAI

from llm_gateway._retry import is_retryable_error
from llm_gateway.exceptions import LLMProviderError
from llm_gateway.types import GenerationConfig, LLMResponse, Message, TokenUsage


class OpenAIProvider:
    name = "openai"

    def __init__(self, api_key: str, model: str):
        self.model = model
        self._client = AsyncOpenAI(api_key=api_key)

    @staticmethod
    def _to_payload(messages: list[Message]) -> list[dict]:
        return [{"role": m.role, "content": m.content} for m in messages]

    @staticmethod
    def _apply_config(kwargs: dict, config: GenerationConfig | None) -> dict:
        if config is None:
            return kwargs
        if config.temperature is not None:
            kwargs["temperature"] = config.temperature
        if config.json_mode:
            kwargs["response_format"] = {"type": "json_object"}
        return kwargs

    async def chat(self, messages: list[Message], config: GenerationConfig | None = None) -> LLMResponse:
        kwargs = self._apply_config(
            {"model": self.model, "messages": self._to_payload(messages)},
            config,
        )
        try:
            response = await self._client.chat.completions.create(**kwargs)
        except Exception as e:
            raise LLMProviderError(self.name, str(e), retryable=is_retryable_error(e)) from e

        text = response.choices[0].message.content or ""
        usage = TokenUsage(
            input_tokens=response.usage.prompt_tokens if response.usage else 0,
            output_tokens=response.usage.completion_tokens if response.usage else 0,
        )
        return LLMResponse(text=text, usage=usage, provider=self.name, model=self.model)

    async def chat_stream(
        self, messages: list[Message], config: GenerationConfig | None = None
    ) -> AsyncIterator[str | TokenUsage]:
        kwargs = self._apply_config(
            {
                "model": self.model,
                "messages": self._to_payload(messages),
                "stream": True,
                "stream_options": {"include_usage": True},
            },
            config,
        )
        try:
            stream = await self._client.chat.completions.create(**kwargs)
            async for chunk in stream:
                if chunk.choices and chunk.choices[0].delta.content:
                    yield chunk.choices[0].delta.content
                if chunk.usage:
                    yield TokenUsage(
                        input_tokens=chunk.usage.prompt_tokens,
                        output_tokens=chunk.usage.completion_tokens,
                    )
        except Exception as e:
            raise LLMProviderError(self.name, str(e), retryable=is_retryable_error(e)) from e
