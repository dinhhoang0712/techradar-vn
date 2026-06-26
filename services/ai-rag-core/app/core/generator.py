import asyncio
import logging
from functools import lru_cache

from app.config import get_settings

logger = logging.getLogger("ai-rag-core.generator")

_MAX_RETRIES = 3
_RETRY_DELAY = 5  # seconds


@lru_cache(maxsize=1)
def get_llm():
    """Khởi tạo LLM một lần duy nhất — tuỳ LLM_PROVIDER trong config (openai | gemini)."""
    settings = get_settings()

    if settings.llm_provider == "openai":
        from langchain_openai import ChatOpenAI
        return ChatOpenAI(
            model=settings.llm_model,
            openai_api_key=settings.openai_api_key,
            temperature=0.2,
        )
    else:
        from langchain_google_genai import ChatGoogleGenerativeAI
        return ChatGoogleGenerativeAI(
            model=settings.llm_model,
            google_api_key=settings.gemini_api_key,
            temperature=0.2,
        )


async def generate(messages: list[dict]) -> str:
    """
    Gọi LLM và trả về text câu trả lời.
    Tự retry khi gặp lỗi server (503, overloaded, rate limit).

    messages: output của prompt_builder.build_messages()
              [{"role": "system", "content": ...}, {"role": "user", "content": ...}]
    """
    from langchain_core.messages import HumanMessage, SystemMessage

    settings = get_settings()
    llm = get_llm()

    lc_messages = []
    for m in messages:
        if m["role"] == "system":
            lc_messages.append(SystemMessage(content=m["content"]))
        else:
            lc_messages.append(HumanMessage(content=m["content"]))

    for attempt in range(1, _MAX_RETRIES + 1):
        try:
            response = await llm.ainvoke(lc_messages)
            return response.content

        except Exception as e:
            err = str(e).lower()
            is_retryable = any(x in err for x in ["503", "429", "service unavailable", "overloaded", "rate limit"])
            if is_retryable and attempt < _MAX_RETRIES:
                logger.warning("%s bận/rate limit, thử lại sau %ds (lần %d/%d)...",
                               settings.llm_provider, _RETRY_DELAY, attempt, _MAX_RETRIES)
                await asyncio.sleep(_RETRY_DELAY)
            else:
                raise RuntimeError(f"LLM ({settings.llm_provider}) lỗi: {e}") from e
