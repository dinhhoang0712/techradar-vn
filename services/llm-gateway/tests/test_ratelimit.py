"""Test RedisRateLimiter với 1 fake Redis client in-memory — không cần Redis thật đang chạy,
để test chạy được offline/CI. Hành vi thật với Redis (INCRBY/EXPIRE) đã verify tay bằng
`redis-cli` khi viết module này.
"""

import pytest

from llm_gateway.ratelimit import RedisRateLimiter


class _FakePipeline:
    def __init__(self, store):
        self._store = store
        self._ops = []

    async def __aenter__(self):
        return self

    async def __aexit__(self, *exc):
        return False

    def incrby(self, key, amount):
        self._ops.append(("incrby", key, amount))

    def expire(self, key, seconds):
        self._ops.append(("expire", key, seconds))

    async def execute(self):
        for op, key, value in self._ops:
            if op == "incrby":
                self._store[key] = self._store.get(key, 0) + value
            # expire: bỏ qua TTL thật trong fake, không cần cho test logic đếm token


class _FakeRedis:
    def __init__(self):
        self.store: dict[str, int] = {}

    async def get(self, key):
        return self.store.get(key)

    def pipeline(self, transaction=True):
        return _FakePipeline(self.store)

    async def aclose(self):
        pass


@pytest.fixture
def limiter(monkeypatch):
    fake = _FakeRedis()
    rl = RedisRateLimiter("redis://ignored", limits={"groq": 100})
    rl._redis = fake  # bỏ qua kết nối Redis thật, tiêm fake client vào
    return rl


@pytest.mark.asyncio
async def test_allow_true_when_under_limit(limiter):
    assert await limiter.allow("groq") is True


@pytest.mark.asyncio
async def test_allow_false_after_exceeding_limit(limiter):
    await limiter.record("groq", 60)
    assert await limiter.allow("groq") is True  # 60 < 100
    await limiter.record("groq", 50)
    assert await limiter.allow("groq") is False  # 110 >= 100


@pytest.mark.asyncio
async def test_provider_without_configured_limit_always_allowed(limiter):
    assert await limiter.allow("openai") is True
    await limiter.record("openai", 10_000_000)
    assert await limiter.allow("openai") is True
