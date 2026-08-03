"""Rate limit theo số token/phút cho từng provider — state nằm ở Redis (dùng chung
giữa mọi instance/service gọi gateway), không giữ counter trong biến Python.

Fixed window đơn giản (giống INCR+EXPIRE bên AiProxyRateLimiterService.java): mỗi phút là
1 "cửa sổ" riêng, key tự hết hạn nên không cần job dọn dẹp.
"""

from __future__ import annotations

import time
from typing import Protocol, runtime_checkable


@runtime_checkable
class RateLimiter(Protocol):
    async def allow(self, provider: str) -> bool:
        """True nếu provider này còn hạn mức trong cửa sổ hiện tại."""
        ...

    async def record(self, provider: str, tokens: int) -> None:
        """Ghi nhận đã dùng bấy nhiêu token cho provider này trong cửa sổ hiện tại."""
        ...


class RedisRateLimiter:
    def __init__(self, redis_url: str, limits: dict[str, int], *, window_seconds: int = 60):
        """
        limits: {"groq": 500_000, "openai": 200_000, ...} — số token tối đa/phút cho mỗi provider.
                Provider không có trong dict này -> không bị giới hạn (luôn allow).
        """
        import redis.asyncio as redis

        self._redis = redis.from_url(redis_url, decode_responses=True)
        self._limits = limits
        self._window = window_seconds

    def _key(self, provider: str) -> str:
        window_id = int(time.time() // self._window)
        return f"llm_gateway:ratelimit:{provider}:{window_id}"

    async def allow(self, provider: str) -> bool:
        limit = self._limits.get(provider)
        if limit is None:
            return True
        used = await self._redis.get(self._key(provider))
        return int(used or 0) < limit

    async def record(self, provider: str, tokens: int) -> None:
        if provider not in self._limits:
            return
        key = self._key(provider)
        async with self._redis.pipeline(transaction=True) as pipe:
            pipe.incrby(key, tokens)
            pipe.expire(key, self._window)
            await pipe.execute()

    async def close(self) -> None:
        await self._redis.aclose()
