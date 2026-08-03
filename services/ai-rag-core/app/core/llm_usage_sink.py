"""on_usage callback cho LLMGateway — ghi 1 dòng billing log vào Postgres mỗi khi
gateway gọi provider thành công. Đây là chỗ "Billing" trong pipeline nằm."""

import logging

from app.db.postgres_client import get_session_factory
from app.models.llm_usage_log import LLMUsageLog

logger = logging.getLogger("ai-rag-core.llm_usage_sink")

_SERVICE_NAME = "ai-rag-core"


async def log_usage_to_postgres(record) -> None:
    """record: llm_gateway.types.UsageRecord.

    Lỗi khi ghi log KHÔNG được làm hỏng response đã trả cho user — chỉ log warning,
    không raise. Billing log là thứ phụ, không phải đường đi chính của request.
    """
    try:
        factory = get_session_factory()
        async with factory() as session:
            session.add(
                LLMUsageLog(
                    service=_SERVICE_NAME,
                    provider=record.provider,
                    model=record.model,
                    input_tokens=record.usage.input_tokens,
                    output_tokens=record.usage.output_tokens,
                    cost_usd=record.cost_usd,
                    fallback_from=record.fallback_from,
                )
            )
            await session.commit()
    except Exception:
        logger.warning("Không ghi được llm_usage_log, bỏ qua (không ảnh hưởng response)", exc_info=True)
