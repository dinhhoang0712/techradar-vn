from unittest.mock import AsyncMock, MagicMock

import pytest
from conf.config import LabelingParams, Settings
from labeling.llm_labeler import _build_provider, build_gateway, call_gemini
from llm_gateway.exceptions import AllProvidersFailedError
from llm_gateway.types import LLMResponse, TokenUsage


def _settings(**overrides) -> Settings:
    base = {
        "neo4j_uri": "bolt://localhost:7687",
        "neo4j_password": "x",
        "gemini_api_key": "",
        "openai_api_key": "",
        "groq_api_key": "",
    }
    base.update(overrides)
    return Settings(**base)


def _params(**overrides) -> LabelingParams:
    return LabelingParams(**overrides)


def test_build_provider_returns_none_without_api_key():
    settings = _settings(gemini_api_key="")
    assert _build_provider("gemini", settings, _params()) is None


def test_build_provider_returns_instance_with_api_key():
    settings = _settings(gemini_api_key="fake-key")
    provider = _build_provider("gemini", settings, _params(gemini_model="gemini-2.5-flash"))
    assert provider is not None
    assert provider.model == "gemini-2.5-flash"


def test_build_gateway_puts_primary_provider_first_and_skips_missing_keys():
    settings = _settings(gemini_api_key="fake-gemini", openai_api_key="fake-openai", groq_api_key="")
    gateway = build_gateway(_params(provider="openai"), settings=settings)

    assert [p.name for p in gateway.providers] == ["openai", "gemini"]


def test_call_gemini_returns_parsed_dict_from_gateway_response(monkeypatch, tmp_path):
    fake_gateway = MagicMock()
    fake_gateway.chat = AsyncMock(
        return_value=LLMResponse(
            text='{"label": "AI/ML", "label_en": "AI/ML", "description": "d", "domain": "AI/ML",'
            ' "confidence": 0.9, "outliers": []}',
            usage=TokenUsage(10, 5),
            provider="gemini",
            model="gemini-2.5-flash",
        )
    )

    data = call_gemini("prompt bất kỳ", _params(), gateway=fake_gateway)

    assert data["label"] == "AI/ML"
    assert data["domain"] == "AI/ML"
    fake_gateway.chat.assert_awaited_once()


def test_call_gemini_strips_markdown_fence(monkeypatch):
    fake_gateway = MagicMock()
    fenced = (
        "```json\n"
        '{"label": "x", "label_en": "x", "description": "d", "domain": "Other",'
        ' "confidence": 0.5, "outliers": []}\n'
        "```"
    )
    fake_gateway.chat = AsyncMock(
        return_value=LLMResponse(text=fenced, usage=TokenUsage(1, 1), provider="gemini", model="m")
    )

    data = call_gemini("prompt", _params(), gateway=fake_gateway)

    assert data["label"] == "x"


def test_call_gemini_retries_on_unparseable_response_then_succeeds():
    fake_gateway = MagicMock()
    good = (
        '{"label": "x", "label_en": "x", "description": "d", "domain": "Other",'
        ' "confidence": 0.5, "outliers": []}'
    )
    fake_gateway.chat = AsyncMock(
        side_effect=[
            LLMResponse(text="không phải JSON", usage=TokenUsage(1, 1), provider="gemini", model="m"),
            LLMResponse(text=good, usage=TokenUsage(1, 1), provider="gemini", model="m"),
        ]
    )

    data = call_gemini("prompt", _params(), gateway=fake_gateway)

    assert data["label"] == "x"
    assert fake_gateway.chat.await_count == 2


def test_call_gemini_raises_after_exhausting_parse_retries():
    fake_gateway = MagicMock()
    fake_gateway.chat = AsyncMock(
        return_value=LLMResponse(text="vẫn không phải JSON", usage=TokenUsage(1, 1), provider="gemini", model="m")
    )

    with pytest.raises(RuntimeError):
        call_gemini("prompt", _params(), gateway=fake_gateway)


def test_call_gemini_raises_runtime_error_when_all_providers_fail():
    fake_gateway = MagicMock()
    fake_gateway.chat = AsyncMock(side_effect=AllProvidersFailedError([]))

    with pytest.raises(RuntimeError, match="LLM lỗi"):
        call_gemini("prompt", _params(), gateway=fake_gateway)


def test_call_gemini_uses_disk_cache(tmp_path):
    fake_gateway = MagicMock()
    good = (
        '{"label": "cached", "label_en": "x", "description": "d", "domain": "Other",'
        ' "confidence": 0.5, "outliers": []}'
    )
    fake_gateway.chat = AsyncMock(
        return_value=LLMResponse(text=good, usage=TokenUsage(1, 1), provider="gemini", model="m")
    )

    data1 = call_gemini("prompt giống nhau", _params(), cache_dir=str(tmp_path), gateway=fake_gateway)
    data2 = call_gemini("prompt giống nhau", _params(), cache_dir=str(tmp_path), gateway=fake_gateway)

    assert data1 == data2 == {
        "label": "cached",
        "label_en": "x",
        "description": "d",
        "domain": "Other",
        "confidence": 0.5,
        "outliers": [],
    }
    fake_gateway.chat.assert_awaited_once()  # lần 2 phải đọc cache, không gọi lại gateway
