"""
Summarization Service — tóm tắt xu hướng công nghệ từ bài viết Neo4j.
Dùng MapReduce để tránh token overflow khi có nhiều articles.
"""
import logging
from datetime import datetime, timezone
from pathlib import Path

from app.api.schemas import SummarizeRequest, SummarizeResponse
from app.core.generator import generate
from app.db.neo4j_client import run_query

logger = logging.getLogger("ai-rag-core.summarize")

_PROMPTS_DIR = Path(__file__).parent.parent / "prompts"


def _load_template(filename: str) -> str:
    return (_PROMPTS_DIR / filename).read_text(encoding="utf-8").strip()


def _parse_period(period: str | None) -> tuple[str, str]:
    """
    Chuyển period string thành date range.
    VD: "2024-Q4" → ("2024-10-01", "2024-12-31")
        "2024-12" → ("2024-12-01", "2024-12-31")
        None → 3 tháng gần nhất
    """
    if not period:
        from datetime import timedelta
        end = datetime.now(tz=timezone.utc)
        start = end - timedelta(days=90)
        return start.strftime("%Y-%m-%d"), end.strftime("%Y-%m-%d")

    if "Q" in period:
        year, q = period.split("-Q")
        q = int(q)
        month_start = (q - 1) * 3 + 1
        month_end = q * 3
        return f"{year}-{month_start:02d}-01", f"{year}-{month_end:02d}-{'30' if month_end in [6,9,11] else '31'}"

    if len(period) == 7:  # YYYY-MM
        year, month = period.split("-")
        return f"{year}-{month}-01", f"{year}-{month}-31"

    return f"{period}-01-01", f"{period}-12-31"


async def _fetch_articles(tech_name: str, start_date: str, end_date: str) -> list[dict]:
    """Neo4j: bài viết MENTIONS tech trong khoảng thời gian."""
    try:
        rows = await run_query(
            """
            MATCH (a:Article)-[:MENTIONS]->(t:Technology)
            WHERE toLower(t.name) = toLower($name)
              AND a.published_date >= date($start)
              AND a.published_date <= date($end)
            RETURN a.title AS title, a.content AS content,
                   a.published_date AS published_date,
                   a.sentiment_score AS sentiment_score
            ORDER BY a.published_date DESC
            LIMIT 20
            """,
            {"name": tech_name, "start": start_date, "end": end_date},
        )
        return rows
    except Exception as e:
        logger.warning("fetch_articles failed: %s", e)
        return []


async def handle(req: SummarizeRequest) -> SummarizeResponse:
    start_date, end_date = _parse_period(req.period)
    period_label = req.period or f"{start_date[:7]} → {end_date[:7]}"

    # 1. Lấy articles từ Neo4j
    articles = await _fetch_articles(req.tech_name, start_date, end_date)

    if not articles:
        return SummarizeResponse(
            tech_name=req.tech_name,
            period=period_label,
            summary=f"Không tìm thấy bài viết nào về {req.tech_name} trong kỳ {period_label}.",
            key_points=[],
            sources_used=0,
        )

    # 2. Build articles text (truncate để tránh token overflow)
    articles_text = "\n\n".join(
        f"[{i+1}] {a.get('title', '')}\n{str(a.get('content', ''))[:600]}"
        for i, a in enumerate(articles)
    )

    # 3. LLM summarize
    template = _load_template("summarize_template.txt")
    prompt = template.format(
        tech_name=req.tech_name,
        period=period_label,
        article_count=len(articles),
        articles_text=articles_text,
        format=req.format,
    )

    messages = [
        {
            "role": "system",
            "content": "Bạn là chuyên gia phân tích công nghệ. Tóm tắt ngắn gọn, dựa hoàn toàn vào bài viết được cung cấp.",
        },
        {"role": "user", "content": prompt},
    ]

    summary_text = await generate(messages)

    # 4. Trích key points (dùng LLM nhỏ hơn)
    key_points_messages = [
        {
            "role": "system",
            "content": "Trích 3-5 điểm nổi bật nhất từ tóm tắt. Trả về JSON array: [\"điểm 1\", \"điểm 2\", ...]",
        },
        {"role": "user", "content": f"Tóm tắt:\n{summary_text}"},
    ]

    key_points: list[str] = []
    try:
        raw = await generate(key_points_messages)
        import json, re
        match = re.search(r"\[.*\]", raw, re.DOTALL)
        if match:
            key_points = json.loads(match.group())
    except Exception:
        pass

    return SummarizeResponse(
        tech_name=req.tech_name,
        period=period_label,
        summary=summary_text,
        key_points=key_points,
        sources_used=len(articles),
    )
