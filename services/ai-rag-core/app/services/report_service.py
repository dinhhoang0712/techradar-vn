"""
Report Generator Service — tạo báo cáo xu hướng tổng hợp theo period.
  1. PostgreSQL: top tech tăng trưởng nhất trong period
  2. Neo4j: top tech được mention nhiều nhất
  3. LLM: tổng hợp thành báo cáo markdown
"""
import logging
from datetime import datetime, timezone
from pathlib import Path

from sqlalchemy import text

from app.api.schemas import ReportRequest, ReportResponse
from app.core.generator import generate
from app.db.neo4j_client import run_query
from app.db.postgres_client import get_session_factory

logger = logging.getLogger("ai-rag-core.report")

_PROMPTS_DIR = Path(__file__).parent.parent / "prompts"


def _parse_period_dates(period: str) -> tuple[str, str]:
    """Giống summarize_service._parse_period nhưng dành cho report."""
    if "Q" in period:
        year, q = period.split("-Q")
        q = int(q)
        month_start = (q - 1) * 3 + 1
        month_end = q * 3
        return f"{year}-{month_start:02d}-01", f"{year}-{month_end:02d}-31"
    if len(period) == 7:
        year, month = period.split("-")
        return f"{year}-{month}-01", f"{year}-{month}-31"
    return f"{period}-01-01", f"{period}-12-31"


async def _top_growing_techs(start_date: str, end_date: str, top_n: int) -> list[dict]:
    """PostgreSQL: top tech tăng trưởng nhất trong khoảng thời gian."""
    factory = get_session_factory()
    async with factory() as session:
        try:
            result = await session.execute(
                text("""
                    SELECT
                        technology_name,
                        AVG(growth_rate) AS avg_growth,
                        SUM(job_count)   AS total_jobs,
                        SUM(article_count) AS total_articles,
                        MAX(mom_growth)  AS peak_mom
                    FROM tech_analytics
                    WHERE month BETWEEN :start AND :end
                    GROUP BY technology_name
                    HAVING AVG(growth_rate) IS NOT NULL
                    ORDER BY AVG(growth_rate) DESC NULLS LAST
                    LIMIT :top_n
                """),
                {"start": start_date, "end": end_date, "top_n": top_n},
            )
            return [dict(r._mapping) for r in result]
        except Exception as e:
            logger.warning("top_growing_techs query failed: %s", e)
            return []


async def _top_mentioned_techs(start_date: str, end_date: str, limit: int = 10) -> list[dict]:
    """Neo4j: tech được mention nhiều nhất trong khoảng thời gian."""
    try:
        rows = await run_query(
            """
            MATCH (a:Article)-[:MENTIONS]->(t:Technology)
            WHERE a.published_date >= date($start)
              AND a.published_date <= date($end)
            RETURN t.name AS tech_name, count(a) AS mention_count
            ORDER BY mention_count DESC
            LIMIT $limit
            """,
            {"start": start_date, "end": end_date, "limit": limit},
        )
        return rows
    except Exception as e:
        logger.warning("top_mentioned_techs query failed: %s", e)
        return []


async def handle(req: ReportRequest) -> ReportResponse:
    start_date, end_date = _parse_period_dates(req.period)
    now_str = datetime.now(tz=timezone.utc).strftime("%Y-%m-%d %H:%M UTC")

    # 1. PostgreSQL: top growing
    top_growing = await _top_growing_techs(start_date, end_date, req.top_n)

    # 2. Neo4j: top mentioned
    top_mentioned = await _top_mentioned_techs(start_date, end_date)

    # 3. Prepare top_techs list (merge 2 sources)
    seen: set[str] = set()
    top_techs: list[dict] = []
    for item in top_growing:
        name = item.get("technology_name", "")
        if name not in seen:
            seen.add(name)
            top_techs.append({
                "name":        name,
                "growth_rate": item.get("avg_growth"),
                "job_count":   item.get("total_jobs"),
                "source":      "analytics",
            })

    for item in top_mentioned:
        name = item.get("tech_name", "")
        if name not in seen:
            seen.add(name)
            top_techs.append({
                "name":          name,
                "mention_count": item.get("mention_count"),
                "source":        "articles",
            })

    # 4. LLM generate report
    growing_lines = "\n".join(
        f"- {t['name']}: tăng trưởng {t.get('growth_rate') or 0:+.1f}%, {t.get('job_count', 0)} việc làm"
        for t in top_growing[:10]
    )
    mentioned_lines = "\n".join(
        f"- {t['tech_name']}: {t.get('mention_count', 0)} bài viết"
        for t in top_mentioned[:10]
    )

    messages = [
        {
            "role": "system",
            "content": (
                "Bạn là chuyên gia phân tích xu hướng công nghệ IT Việt Nam. "
                "Viết báo cáo ngắn gọn (500-800 từ) bằng tiếng Việt, định dạng Markdown. "
                "Chỉ dùng dữ liệu được cung cấp."
            ),
        },
        {
            "role": "user",
            "content": (
                f"Báo cáo xu hướng công nghệ kỳ {req.period} ({start_date} → {end_date})\n\n"
                f"Top {req.top_n} công nghệ tăng trưởng nhất (theo analytics):\n{growing_lines}\n\n"
                f"Top công nghệ được nhắc nhiều nhất (theo bài viết):\n{mentioned_lines}\n\n"
                "Hãy viết báo cáo tổng hợp với: tóm tắt tổng quan, highlights, nhận xét xu hướng chính."
            ),
        },
    ]

    report_text = await generate(messages)

    if req.format == "json":
        import json
        report_text = json.dumps(
            {"period": req.period, "top_techs": top_techs, "summary": report_text},
            ensure_ascii=False,
            indent=2,
        )

    return ReportResponse(
        period=req.period,
        report=report_text,
        top_techs=top_techs,
        generated_at=now_str,
    )
