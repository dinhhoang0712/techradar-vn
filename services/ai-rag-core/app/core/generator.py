from functools import lru_cache

from llm_gateway import LLMGateway, Message
from llm_gateway.exceptions import AllProvidersFailedError
from llm_gateway.ratelimit import RedisRateLimiter

from app.config import get_settings
from app.core.llm_usage_sink import log_usage_to_postgres

_MAX_RETRIES = 3
_RETRY_DELAY = 5  # seconds


@lru_cache(maxsize=1)
def get_llm():
    """Trả về LLM object của LangChain (KHÔNG qua llm_gateway) — chỉ dùng cho
    app/agent/executor.py, vì LangChain's create_tool_calling_agent() cần 1 object có
    .bind_tools()/.invoke() theo interface của LangChain, không phải hàm trả text thuần
    như get_gateway() dưới đây. Do đi ngoài gateway, đường này KHÔNG có fallback/cost
    tracking — phạm vi cố ý thu hẹp, không phải thiếu sót.
    """
    settings = get_settings()

    if settings.llm_provider == "openai":
        from langchain_openai import ChatOpenAI

        return ChatOpenAI(model=settings.llm_model, openai_api_key=settings.openai_api_key, temperature=0.2)
    elif settings.llm_provider == "groq":
        from langchain_groq import ChatGroq

        return ChatGroq(model=settings.llm_model, groq_api_key=settings.groq_api_key, temperature=0.2)
    else:
        from langchain_google_genai import ChatGoogleGenerativeAI

        return ChatGoogleGenerativeAI(model=settings.llm_model, google_api_key=settings.gemini_api_key, temperature=0.2)

# Thứ tự thử khi provider chính (llm_provider) lỗi/hết rate limit — có key trong .env
# mới được đưa vào danh sách; không có key thì tự bỏ qua, không lỗi.
_PROVIDER_ORDER = ("openai", "groq", "gemini", "claude")

# provider chính (llm_provider) dùng llm_model như hành vi cũ; các provider CHỈ đóng vai
# fallback dùng model riêng theo field tương ứng dưới đây.
_FALLBACK_MODEL_FIELD = {
    "openai": "openai_model",
    "groq": "groq_model",
    "gemini": "gemini_model",
    "claude": "anthropic_model",
}
_API_KEY_FIELD = {
    "openai": "openai_api_key",
    "groq": "groq_api_key",
    "gemini": "gemini_api_key",
    "claude": "anthropic_api_key",
}


def _build_provider(name: str, settings):
    api_key = getattr(settings, _API_KEY_FIELD[name])
    if not api_key:
        return None  # không có key -> bỏ qua, không phải lỗi

    model = settings.llm_model if name == settings.llm_provider else getattr(settings, _FALLBACK_MODEL_FIELD[name])

    if name == "openai":
        from llm_gateway.providers.openai_provider import OpenAIProvider

        return OpenAIProvider(api_key=api_key, model=model)
    if name == "groq":
        from llm_gateway.providers.groq_provider import GroqProvider

        return GroqProvider(api_key=api_key, model=model)
    if name == "gemini":
        from llm_gateway.providers.gemini_provider import GeminiProvider

        return GeminiProvider(api_key=api_key, model=model)
    if name == "claude":
        from llm_gateway.providers.claude_provider import ClaudeProvider

        return ClaudeProvider(api_key=api_key, model=model)
    return None


@lru_cache(maxsize=1)
def get_gateway() -> LLMGateway:
    """Khởi tạo LLMGateway một lần duy nhất.

    Provider chính = LLM_PROVIDER trong config (giữ đúng hành vi cũ trước khi có gateway).
    Provider khác có API key trong .env tự động thành fallback chain theo _PROVIDER_ORDER —
    provider chính lỗi/hết rate limit thì gateway tự rơi sang provider kế tiếp còn key,
    không cần cấu hình gì thêm ngoài điền API key.

    Rate limit: wire RedisRateLimiter với limits={} (không giới hạn provider nào theo mặc định —
    an toàn, không tự nhiên chặn traffic hiện tại). Muốn bật giới hạn token/phút cho provider nào,
    sửa `limits=` ở đây, ví dụ {"groq": 500_000}.
    """
    settings = get_settings()

    order = [settings.llm_provider] + [p for p in _PROVIDER_ORDER if p != settings.llm_provider]
    providers = [p for name in order if (p := _build_provider(name, settings)) is not None]

    rate_limiter = RedisRateLimiter(settings.redis_url, limits={}) if settings.redis_url else None

    return LLMGateway(
        providers,
        rate_limiter=rate_limiter,
        on_usage=log_usage_to_postgres,
        max_retries=_MAX_RETRIES,
        retry_delay=_RETRY_DELAY,
    )


def _to_gateway_messages(messages: list[dict]) -> list[Message]:
    return [Message(role="system" if m["role"] == "system" else "user", content=m["content"]) for m in messages]


async def generate(messages: list[dict]) -> str:
    """
    Gọi LLM và trả về text câu trả lời.
    Retry cùng provider khi lỗi tạm thời (503/429/overloaded), rồi tự fallback sang provider
    khác nếu vẫn lỗi hoặc hết rate limit — xem get_gateway().

    messages: output của prompt_builder.build_messages()
              [{"role": "system", "content": ...}, {"role": "user", "content": ...}]
    """
    gateway = get_gateway()
    try:
        response = await gateway.chat(_to_gateway_messages(messages))
    except AllProvidersFailedError as e:
        raise RuntimeError(f"LLM lỗi: {e}") from e
    return response.text
