import pytest

from llm_gateway.exceptions import AllProvidersFailedError, LLMProviderError
from llm_gateway.gateway import LLMGateway
from llm_gateway.types import LLMResponse, Message, TokenUsage


class FakeProvider:
    """Provider giả để test logic retry/fallback của gateway mà không gọi API thật."""

    def __init__(self, name, model="fake-model", fail_times=0, retryable=True):
        self.name = name
        self.model = model
        self.fail_times = fail_times
        self.retryable = retryable
        self.calls = 0

    async def chat(self, messages, config=None):
        self.calls += 1
        if self.calls <= self.fail_times:
            raise LLMProviderError(self.name, "lỗi giả lập", retryable=self.retryable)
        return LLMResponse(
            text=f"reply from {self.name}", usage=TokenUsage(10, 5), provider=self.name, model=self.model
        )

    async def chat_stream(self, messages, config=None):
        self.calls += 1
        if self.calls <= self.fail_times:
            raise LLMProviderError(self.name, "lỗi giả lập stream", retryable=self.retryable)
        yield f"chunk from {self.name}"
        yield TokenUsage(10, 5)


class FlakyMidStreamProvider:
    """Lỗi SAU KHI đã stream ra 1 chunk — dùng để test gateway không fallback giữa dòng."""

    name = "flaky"
    model = "fake-model"

    def __init__(self):
        self.calls = 0

    async def chat_stream(self, messages, config=None):
        self.calls += 1
        yield "chunk-1"
        raise LLMProviderError(self.name, "đứt giữa dòng", retryable=True)


@pytest.mark.asyncio
async def test_chat_uses_primary_when_healthy():
    primary = FakeProvider("primary")
    backup = FakeProvider("backup")
    gateway = LLMGateway([primary, backup])

    response = await gateway.chat([Message("user", "hi")])

    assert response.provider == "primary"
    assert backup.calls == 0


@pytest.mark.asyncio
async def test_chat_falls_back_when_primary_fails_non_retryable():
    primary = FakeProvider("primary", fail_times=99, retryable=False)
    backup = FakeProvider("backup")
    gateway = LLMGateway([primary, backup], max_retries=2, retry_delay=0)

    response = await gateway.chat([Message("user", "hi")])

    assert response.provider == "backup"
    assert primary.calls == 1  # lỗi non-retryable -> gọi đúng 1 lần rồi rơi luôn sang backup


@pytest.mark.asyncio
async def test_chat_retries_same_provider_before_falling_back():
    primary = FakeProvider("primary", fail_times=1, retryable=True)  # lỗi 1 lần rồi thành công
    backup = FakeProvider("backup")
    gateway = LLMGateway([primary, backup], max_retries=3, retry_delay=0)

    response = await gateway.chat([Message("user", "hi")])

    assert response.provider == "primary"
    assert primary.calls == 2
    assert backup.calls == 0


@pytest.mark.asyncio
async def test_chat_raises_when_all_providers_fail():
    primary = FakeProvider("primary", fail_times=99, retryable=False)
    backup = FakeProvider("backup", fail_times=99, retryable=False)
    gateway = LLMGateway([primary, backup], max_retries=1, retry_delay=0)

    with pytest.raises(AllProvidersFailedError):
        await gateway.chat([Message("user", "hi")])


@pytest.mark.asyncio
async def test_on_usage_reports_cost_and_fallback_from():
    primary = FakeProvider("primary", fail_times=99, retryable=False)
    backup = FakeProvider("backup", model="claude-sonnet-5")
    records = []

    async def on_usage(record):
        records.append(record)

    gateway = LLMGateway([primary, backup], on_usage=on_usage, max_retries=1, retry_delay=0)
    await gateway.chat([Message("user", "hi")])

    assert len(records) == 1
    assert records[0].provider == "backup"
    assert records[0].fallback_from == "primary"
    assert records[0].cost_usd > 0


@pytest.mark.asyncio
async def test_chat_stream_yields_text_and_reports_usage():
    primary = FakeProvider("primary")
    records = []

    async def on_usage(record):
        records.append(record)

    gateway = LLMGateway([primary], on_usage=on_usage)
    chunks = [c async for c in gateway.chat_stream([Message("user", "hi")])]

    assert chunks == ["chunk from primary"]
    assert len(records) == 1
    assert records[0].provider == "primary"


@pytest.mark.asyncio
async def test_chat_stream_does_not_fallback_after_partial_output():
    flaky = FlakyMidStreamProvider()
    backup = FakeProvider("backup")
    gateway = LLMGateway([flaky, backup], max_retries=3, retry_delay=0)

    with pytest.raises(AllProvidersFailedError):
        async for _ in gateway.chat_stream([Message("user", "hi")]):
            pass

    assert backup.calls == 0  # đã lỡ stream 1 chunk cho caller -> không được fallback sang backup
