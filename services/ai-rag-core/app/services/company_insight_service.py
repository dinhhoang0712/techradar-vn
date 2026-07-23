"""
Company Insight Service — sinh nhận định AI ngắn gọn về 1 công ty dựa trên tech stack/quy mô
tuyển dụng suy ra từ Neo4j. Cùng cấu trúc với summarize_service nhưng theo entity Company.
"""

import json
import logging
import re
from pathlib import Path

from app.api.schemas import CompanyInsightRequest, CompanyInsightResponse
from app.core.generator import generate
from app.db.graph_queries import COMPANY_INSIGHT_CONTEXT
from app.db.neo4j_client import run_query

logger = logging.getLogger("ai-rag-core.company_insight")

_PROMPTS_DIR = Path(__file__).parent.parent / "prompts"


def _load_template(filename: str) -> str:
    return (_PROMPTS_DIR / filename).read_text(encoding="utf-8").strip()


async def _fetch_company_context(company_name: str) -> dict | None:
    try:
        rows = await run_query(COMPANY_INSIGHT_CONTEXT, {"company_name": company_name})
        return rows[0] if rows else None
    except Exception as e:
        logger.warning("fetch_company_context failed: %s", e)
        return None


async def handle(req: CompanyInsightRequest) -> CompanyInsightResponse:
    context = await _fetch_company_context(req.company_name)

    if not context or not context.get("job_count"):
        return CompanyInsightResponse(
            company_name=req.company_name,
            summary=f"Chưa có đủ dữ liệu tuyển dụng để đưa ra nhận định về {req.company_name}.",
            highlights=[],
        )

    template = _load_template("company_insight_template.txt")
    prompt = template.format(
        company_name=req.company_name,
        location=context.get("location") or "Chưa rõ",
        industry=context.get("industry") or "Chưa rõ",
        size=context.get("size") or "Chưa rõ",
        job_count=context.get("job_count") or 0,
        tech_stack=", ".join(context.get("tech_stack") or []) or "Chưa rõ",
    )

    messages = [
        {
            "role": "system",
            "content": "Bạn là chuyên gia phân tích thị trường tuyển dụng IT. Nhận định ngắn gọn, "
            "dựa hoàn toàn vào dữ liệu được cung cấp.",
        },
        {"role": "user", "content": prompt},
    ]

    summary_text = await generate(messages)

    highlights_messages = [
        {
            "role": "system",
            "content": 'Trích 2-3 điểm nổi bật nhất từ nhận định. Trả về JSON array: ["điểm 1", "điểm 2"]',
        },
        {"role": "user", "content": f"Nhận định:\n{summary_text}"},
    ]

    highlights: list[str] = []
    try:
        raw = await generate(highlights_messages)
        match = re.search(r"\[.*\]", raw, re.DOTALL)
        if match:
            highlights = json.loads(match.group())
    except Exception:
        pass

    return CompanyInsightResponse(
        company_name=req.company_name,
        summary=summary_text,
        highlights=highlights,
    )
