from unittest.mock import AsyncMock, MagicMock

import pytest
from llm_gateway.exceptions import AllProvidersFailedError
from llm_gateway.types import LLMResponse, TokenUsage

from app.config import get_settings
from app.core.generator import _build_provider, generate, get_gateway
from app.core.generator_stream import generate_stream


@pytest.fixture(autouse=True)
def _clear_caches():
    """get_settings() và get_gateway() đều @lru_cache — phải xoá giữa các test để 1 test
    đổi env var không lọt sang test khác."""
    get_settings.cache_clear()
    get_gateway.cache_clear()
    yield
    get_settings.cache_clear()
    get_gateway.cache_clear()


@pytest.mark.asyncio
async def test_generate_returns_text_from_gateway(monkeypatch):
    fake_gateway = MagicMock()
    fake_gateway.chat = AsyncMock(
        return_value=LLMResponse(text="OK", usage=TokenUsage(1, 1), provider="openai", model="gpt-4o-mini")
    )
    monkeypatch.setattr("app.core.generator.get_gateway", lambda: fake_gateway)

    result = await generate([{"role": "user", "content": "Hi"}])

    assert result == "OK"


@pytest.mark.asyncio
async def test_generate_raises_runtime_error_when_all_providers_fail(monkeypatch):
    fake_gateway = MagicMock()
    fake_gateway.chat = AsyncMock(side_effect=AllProvidersFailedError([]))
    monkeypatch.setattr("app.core.generator.get_gateway", lambda: fake_gateway)

    with pytest.raises(RuntimeError, match="LLM lỗi"):
        await generate([{"role": "user", "content": "Hi"}])


@pytest.mark.asyncio
async def test_generate_stream_yields_chunks_from_gateway(monkeypatch):
    fake_gateway = MagicMock()

    async def fake_chat_stream(messages):
        yield "A"
        yield "B"

    fake_gateway.chat_stream = fake_chat_stream
    # generator_stream.py làm "from app.core.generator import get_gateway" -> patch phải nhắm
    # đúng namespace app.core.generator_stream (nơi generate_stream() thực sự gọi get_gateway()
    # đã import vào), patch app.core.generator.get_gateway KHÔNG có tác dụng ở đây.
    monkeypatch.setattr("app.core.generator_stream.get_gateway", lambda: fake_gateway)

    chunks = [c async for c in generate_stream([{"role": "user", "content": "Hi"}])]

    assert chunks == ["A", "B"]


@pytest.mark.asyncio
async def test_generate_stream_raises_runtime_error_when_all_providers_fail(monkeypatch):
    fake_gateway = MagicMock()

    async def fake_chat_stream(messages):
        raise AllProvidersFailedError([])
        yield  # không bao giờ chạy tới — chỉ để hàm là async generator hợp lệ

    fake_gateway.chat_stream = fake_chat_stream
    monkeypatch.setattr("app.core.generator_stream.get_gateway", lambda: fake_gateway)

    with pytest.raises(RuntimeError, match="LLM lỗi"):
        async for _ in generate_stream([{"role": "user", "content": "Hi"}]):
            pass


def test_build_provider_returns_none_without_api_key(monkeypatch):
    monkeypatch.setenv("OPENAI_API_KEY", "")
    get_settings.cache_clear()

    assert _build_provider("openai", get_settings()) is None


def test_build_provider_uses_llm_model_for_primary_provider(monkeypatch):
    monkeypatch.setenv("LLM_PROVIDER", "groq")
    monkeypatch.setenv("GROQ_API_KEY", "fake-key")
    monkeypatch.setenv("LLM_MODEL", "llama-custom")
    get_settings.cache_clear()

    provider = _build_provider("groq", get_settings())

    assert provider is not None
    assert provider.model == "llama-custom"  # provider CHÍNH dùng llm_model, giữ đúng hành vi cũ


def test_build_provider_uses_fallback_model_for_non_primary_provider(monkeypatch):
    monkeypatch.setenv("LLM_PROVIDER", "openai")
    monkeypatch.setenv("GROQ_API_KEY", "fake-key")
    get_settings.cache_clear()
    settings = get_settings()

    provider = _build_provider("groq", settings)

    assert provider is not None
    assert provider.model == settings.groq_model  # groq không phải provider chính -> dùng groq_model riêng


def test_get_gateway_builds_fallback_chain_only_from_providers_with_api_key(monkeypatch):
    monkeypatch.setenv("LLM_PROVIDER", "openai")
    monkeypatch.setenv("OPENAI_API_KEY", "fake-openai")
    monkeypatch.setenv("GROQ_API_KEY", "fake-groq")
    monkeypatch.setenv("GEMINI_API_KEY", "")
    monkeypatch.setenv("ANTHROPIC_API_KEY", "")
    get_settings.cache_clear()
    get_gateway.cache_clear()

    gateway = get_gateway()

    assert [p.name for p in gateway.providers] == ["openai", "groq"]


def test_get_gateway_puts_primary_provider_first(monkeypatch):
    monkeypatch.setenv("LLM_PROVIDER", "groq")
    monkeypatch.setenv("OPENAI_API_KEY", "fake-openai")
    monkeypatch.setenv("GROQ_API_KEY", "fake-groq")
    monkeypatch.setenv("GEMINI_API_KEY", "")
    monkeypatch.setenv("ANTHROPIC_API_KEY", "")
    get_settings.cache_clear()
    get_gateway.cache_clear()

    gateway = get_gateway()

    assert [p.name for p in gateway.providers] == ["groq", "openai"]
