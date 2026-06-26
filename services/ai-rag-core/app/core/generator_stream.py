import asyncio
import logging
from typing import AsyncIterator

from app.core.generator import get_llm

logger = logging.getLogger("ai-rag-core.generator")

_MAX_RETRIES = 3
_RETRY_DELAY = 5  # seconds


async def generate_stream(messages: list[dict]) -> AsyncIterator[str]:
    """
    Stream câu trả lời từ LLM theo từng chunk.
    Tự retry khi gặp lỗi 503 (server bận) trước khi bắt đầu stream.

    messages: output của prompt_builder.build_messages()
    """
    from langchain_core.messages import HumanMessage, SystemMessage

    llm = get_llm()

    lc_messages = []
    for m in messages:
        if m["role"] == "system":
            lc_messages.append(SystemMessage(content=m["content"]))
        else:
            lc_messages.append(HumanMessage(content=m["content"]))

    last_err: Exception | None = None
    for attempt in range(1, _MAX_RETRIES + 1):
        try:
            async for chunk in llm.astream(lc_messages):
                content = getattr(chunk, "content", "")
                if content:
                    yield content
            return
        except Exception as e:
            last_err = e
            err = str(e).lower()
            if ("503" in err or "service unavailable" in err or "overloaded" in err) and attempt < _MAX_RETRIES:
                logger.warning("server bận, thử lại sau %ds (lần %d/%d)...",
                               _RETRY_DELAY, attempt, _MAX_RETRIES)
                await asyncio.sleep(_RETRY_DELAY)
            else:
                raise RuntimeError(f"LLM lỗi: {e}") from e

    if last_err:
        raise RuntimeError(f"LLM lỗi: {last_err}") from last_err
