from collections.abc import AsyncIterator

from llm_gateway.exceptions import AllProvidersFailedError

from app.core.generator import _to_gateway_messages, get_gateway


async def generate_stream(messages: list[dict]) -> AsyncIterator[str]:
    """
    Stream câu trả lời từ LLM theo từng chunk.
    Dùng cùng gateway (retry + fallback) với generate() ở generator.py — xem get_gateway().

    Khác biệt so với generate(): nếu đã stream ra ít nhất 1 chunk cho caller thì gateway
    KHÔNG fallback giữa dòng nữa (tránh lộn/trùng nội dung đã trả ra) — lỗi giữa dòng sẽ
    raise thẳng lên đây.

    messages: output của prompt_builder.build_messages()
    """
    gateway = get_gateway()
    try:
        async for chunk in gateway.chat_stream(_to_gateway_messages(messages)):
            yield chunk
    except AllProvidersFailedError as e:
        raise RuntimeError(f"LLM lỗi: {e}") from e
