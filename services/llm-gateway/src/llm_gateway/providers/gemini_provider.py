from __future__ import annotations

from collections.abc import AsyncIterator

import google.generativeai as genai

from llm_gateway._retry import is_retryable_error
from llm_gateway.exceptions import LLMProviderError
from llm_gateway.types import GenerationConfig, LLMResponse, Message, TokenUsage


class GeminiProvider:
    """Gemini không có role "system" trong messages — tách ra làm system_instruction riêng,
    và dùng "model" thay cho "assistant" trong lịch sử hội thoại.
    """

    name = "gemini"

    def __init__(self, api_key: str, model: str):
        self.model = model
        # genai.configure() là global cho toàn module — khớp với cách ml-clustering/data-platform
        # đang dùng SDK này, không phải hạn chế riêng của gateway.
        genai.configure(api_key=api_key)

    def _build_model_and_contents(self, messages: list[Message], config: GenerationConfig | None):
        system_parts = [m.content for m in messages if m.role == "system"]
        contents = [
            {"role": "model" if m.role == "assistant" else "user", "parts": [m.content]}
            for m in messages
            if m.role != "system"
        ]

        gen_config_kwargs: dict = {}
        if config is not None:
            if config.temperature is not None:
                gen_config_kwargs["temperature"] = config.temperature
            if config.json_mode:
                gen_config_kwargs["response_mime_type"] = "application/json"
        generation_config = genai.GenerationConfig(**gen_config_kwargs) if gen_config_kwargs else None

        model = genai.GenerativeModel(
            self.model,
            system_instruction="\n\n".join(system_parts) or None,
            generation_config=generation_config,
        )
        return model, contents

    @staticmethod
    def _usage_from(response) -> TokenUsage:
        meta = getattr(response, "usage_metadata", None)
        return TokenUsage(
            input_tokens=getattr(meta, "prompt_token_count", 0) or 0,
            output_tokens=getattr(meta, "candidates_token_count", 0) or 0,
        )

    async def chat(self, messages: list[Message], config: GenerationConfig | None = None) -> LLMResponse:
        model, contents = self._build_model_and_contents(messages, config)
        try:
            response = await model.generate_content_async(contents)
        except Exception as e:
            raise LLMProviderError(self.name, str(e), retryable=is_retryable_error(e)) from e

        return LLMResponse(text=response.text, usage=self._usage_from(response), provider=self.name, model=self.model)

    async def chat_stream(
        self, messages: list[Message], config: GenerationConfig | None = None
    ) -> AsyncIterator[str | TokenUsage]:
        model, contents = self._build_model_and_contents(messages, config)
        try:
            response = await model.generate_content_async(contents, stream=True)
            async for chunk in response:
                if chunk.text:
                    yield chunk.text
        except Exception as e:
            raise LLMProviderError(self.name, str(e), retryable=is_retryable_error(e)) from e

        meta = getattr(response, "usage_metadata", None)
        if meta is not None:
            yield self._usage_from(response)
