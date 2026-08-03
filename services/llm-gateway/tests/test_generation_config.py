import pytest

from llm_gateway.gateway import LLMGateway
from llm_gateway.types import GenerationConfig, LLMResponse, Message, TokenUsage


class RecordingProvider:
    """Ghi lại config nhận được ở mỗi lần gọi — dùng để verify gateway forward đúng
    GenerationConfig (temperature, json_mode) xuống tới provider."""

    name = "recording"
    model = "fake-model"

    def __init__(self):
        self.received_configs = []

    async def chat(self, messages, config=None):
        self.received_configs.append(config)
        return LLMResponse(text="ok", usage=TokenUsage(1, 1), provider=self.name, model=self.model)

    async def chat_stream(self, messages, config=None):
        self.received_configs.append(config)
        yield "ok"
        yield TokenUsage(1, 1)


@pytest.mark.asyncio
async def test_chat_forwards_generation_config_to_provider():
    provider = RecordingProvider()
    gateway = LLMGateway([provider])
    config = GenerationConfig(temperature=0.0, json_mode=True)

    await gateway.chat([Message("user", "hi")], config)

    assert provider.received_configs == [config]


@pytest.mark.asyncio
async def test_chat_without_config_forwards_none():
    provider = RecordingProvider()
    gateway = LLMGateway([provider])

    await gateway.chat([Message("user", "hi")])

    assert provider.received_configs == [None]


@pytest.mark.asyncio
async def test_chat_stream_forwards_generation_config_to_provider():
    provider = RecordingProvider()
    gateway = LLMGateway([provider])
    config = GenerationConfig(temperature=0.5)

    async for _ in gateway.chat_stream([Message("user", "hi")], config):
        pass

    assert provider.received_configs == [config]
