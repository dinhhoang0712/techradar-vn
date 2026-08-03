from unittest.mock import AsyncMock, MagicMock

import pytest
from config import Settings
from gold.tech_dedup import _build_provider, _call_llm, build_gateway
from llm_gateway.exceptions import AllProvidersFailedError
from llm_gateway.types import LLMResponse, TokenUsage


def test_build_provider_returns_none_without_api_key():
    settings = Settings(gemini_api_key="")
    assert _build_provider("gemini", settings) is None


def test_build_provider_returns_instance_with_api_key():
    settings = Settings(gemini_api_key="fake-key", tech_dedup_gemini_model="gemini-2.5-flash")
    provider = _build_provider("gemini", settings)
    assert provider is not None
    assert provider.model == "gemini-2.5-flash"


def test_build_gateway_puts_primary_provider_first_and_skips_missing_keys():
    settings = Settings(
        tech_dedup_llm_provider="openai",
        openai_api_key="fake-openai",
        gemini_api_key="fake-gemini",
        groq_api_key="",
    )
    gateway = build_gateway(settings)

    assert [p.name for p in gateway.providers] == ["openai", "gemini"]


def test_call_llm_returns_text_from_gateway():
    """_call_llm() cố ý là hàm SYNC (bridge asyncio.run() bên trong) để run() — 1 job
    APScheduler đồng bộ — gọi được thẳng, không cần test này là async."""
    fake_gateway = MagicMock()
    fake_gateway.chat = AsyncMock(
        return_value=LLMResponse(
            text='{"groups": [], "categories": []}', usage=TokenUsage(1, 1), provider="gemini", model="m"
        )
    )

    result = _call_llm(["Kubernetes", "K8s"], Settings(), gateway=fake_gateway)

    assert result == '{"groups": [], "categories": []}'
    fake_gateway.chat.assert_awaited_once()


def test_call_llm_raises_runtime_error_when_all_providers_fail():
    fake_gateway = MagicMock()
    fake_gateway.chat = AsyncMock(side_effect=AllProvidersFailedError([]))

    with pytest.raises(RuntimeError, match="LLM lỗi"):
        _call_llm(["Kubernetes"], Settings(), gateway=fake_gateway)
