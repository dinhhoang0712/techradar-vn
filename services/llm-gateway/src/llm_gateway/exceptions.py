class LLMProviderError(Exception):
    """1 provider gọi lỗi. Gateway bắt lỗi này để quyết định fallback."""

    def __init__(self, provider: str, message: str, *, retryable: bool):
        self.provider = provider
        self.retryable = retryable
        super().__init__(f"[{provider}] {message}")


class LLMRateLimitedError(LLMProviderError):
    """Provider đang bị rate-limit (theo giới hạn cấu hình trong gateway, không phải lỗi từ SDK)."""

    def __init__(self, provider: str):
        super().__init__(provider, "vượt rate limit cấu hình cho provider này", retryable=True)


class AllProvidersFailedError(Exception):
    """Toàn bộ provider trong danh sách fallback đều thất bại."""

    def __init__(self, attempts: list[LLMProviderError]):
        self.attempts = attempts
        detail = "; ".join(str(a) for a in attempts)
        super().__init__(f"Tất cả provider đều lỗi: {detail}")
