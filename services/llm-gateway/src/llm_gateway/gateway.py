"""LLMGateway — điểm gọi LLM duy nhất mà các service dùng.

Che giấu bên trong: chọn provider theo thứ tự ưu tiên, retry cùng provider khi lỗi
tạm thời, rơi (fallback) sang provider kế tiếp khi hết lượt retry hoặc bị rate-limit,
tính cost từ usage trả về, và báo usage ra ngoài qua on_usage — gateway không tự viết
vào Postgres/Prometheus, caller (từng service) tự quyết định ghi vào đâu.

Thứ tự chạy thật khi 1 request đi qua (không phải thứ tự đọc code từ trên xuống):
  1. Rate Limit  — hỏi trước khi gọi, provider nào hết hạn mức thì bỏ qua luôn.
  2. Gọi provider — kèm Fallback nếu lỗi/hết hạn mức.
  3. Token Usage — đọc từ response của provider vừa gọi thành công.
  4. Cost Tracking — nhân usage với bảng giá trong pricing.py.
  5. Billing — on_usage callback, caller tự ghi log.
"""

from __future__ import annotations

import asyncio
import logging
from collections.abc import AsyncIterator, Awaitable, Callable

from llm_gateway.exceptions import AllProvidersFailedError, LLMProviderError, LLMRateLimitedError
from llm_gateway.pricing import calc_cost
from llm_gateway.providers.base import LLMProvider
from llm_gateway.ratelimit import RateLimiter
from llm_gateway.types import GenerationConfig, LLMResponse, Message, TokenUsage, UsageRecord

logger = logging.getLogger("llm_gateway")

OnUsage = Callable[[UsageRecord], Awaitable[None]]


class LLMGateway:
    def __init__(
        self,
        providers: list[LLMProvider],
        *,
        rate_limiter: RateLimiter | None = None,
        on_usage: OnUsage | None = None,
        max_retries: int = 3,
        retry_delay: float = 5.0,
    ):
        """
        providers: thứ tự trong list = thứ tự ưu tiên/fallback. providers[0] được thử trước,
                   lỗi/hết hạn mức thì rơi xuống providers[1], v.v.
        """
        if not providers:
            raise ValueError("LLMGateway cần ít nhất 1 provider")
        self.providers = providers
        self.rate_limiter = rate_limiter
        self.on_usage = on_usage
        self.max_retries = max_retries
        self.retry_delay = retry_delay

    async def chat(self, messages: list[Message], config: GenerationConfig | None = None) -> LLMResponse:
        errors: list[LLMProviderError] = []

        for provider in self.providers:
            if self.rate_limiter and not await self.rate_limiter.allow(provider.name):
                logger.warning("provider %s vượt rate limit, chuyển sang provider kế tiếp", provider.name)
                errors.append(LLMRateLimitedError(provider.name))
                continue

            for attempt in range(1, self.max_retries + 1):
                try:
                    response = await provider.chat(messages, config)
                except LLMProviderError as e:
                    errors.append(e)
                    if e.retryable and attempt < self.max_retries:
                        logger.warning(
                            "%s lỗi (lần %d/%d), thử lại sau %.0fs: %s",
                            provider.name,
                            attempt,
                            self.max_retries,
                            self.retry_delay,
                            e,
                        )
                        await asyncio.sleep(self.retry_delay)
                        continue
                    break  # hết lượt retry cho provider này -> rơi xuống provider kế tiếp trong danh sách
                else:
                    fallback_from = errors[-1].provider if errors else None
                    await self._on_success(response, fallback_from=fallback_from)
                    return response

        raise AllProvidersFailedError(errors)

    async def chat_stream(self, messages: list[Message], config: GenerationConfig | None = None) -> AsyncIterator[str]:
        errors: list[LLMProviderError] = []

        for provider in self.providers:
            if self.rate_limiter and not await self.rate_limiter.allow(provider.name):
                errors.append(LLMRateLimitedError(provider.name))
                continue

            for attempt in range(1, self.max_retries + 1):
                started = False
                usage: TokenUsage | None = None
                try:
                    async for item in provider.chat_stream(messages, config):
                        if isinstance(item, TokenUsage):
                            usage = item
                            continue
                        started = True
                        yield item
                except LLMProviderError as e:
                    errors.append(e)
                    if started:
                        # Đã trả chunk text cho caller rồi -> không thể "thử lại từ đầu" mà
                        # không làm trùng/lộn nội dung đã stream ra ngoài. Báo lỗi luôn, không
                        # fallback giữa dòng (khác với chat() không-stream, có thể an toàn retry
                        # toàn bộ vì chưa trả gì cho caller).
                        raise AllProvidersFailedError(errors) from e
                    if e.retryable and attempt < self.max_retries:
                        await asyncio.sleep(self.retry_delay)
                        continue
                    break
                else:
                    if usage is not None:
                        fallback_from = errors[-1].provider if errors else None
                        response = LLMResponse(text="", usage=usage, provider=provider.name, model=provider.model)
                        await self._on_success(response, fallback_from=fallback_from)
                    return

        raise AllProvidersFailedError(errors)

    async def _on_success(self, response: LLMResponse, *, fallback_from: str | None) -> None:
        if self.rate_limiter:
            await self.rate_limiter.record(response.provider, response.usage.total_tokens)
        if self.on_usage:
            cost = calc_cost(response.model, response.usage)
            await self.on_usage(
                UsageRecord(
                    provider=response.provider,
                    model=response.model,
                    usage=response.usage,
                    cost_usd=cost,
                    fallback_from=fallback_from,
                )
            )
