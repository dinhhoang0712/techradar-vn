"""
Forecast Service — dự báo xu hướng công nghệ dựa trên:
  1. Time-series từ tech_analytics (PostgreSQL)
  2. Statistical signals (numpy: linear slope, momentum, volatility)
  3. Neo4j: article sentiment gần đây
  4. LLM synthesis → predicted_direction + reasoning
"""
import logging
from datetime import datetime, timezone
from pathlib import Path

from app.api.schemas import ForecastRequest, ForecastResponse, ForecastSignal
from app.core.generator import generate
from app.core.retriever_sql import sql_tech_timeseries
from app.db.neo4j_client import run_query

logger = logging.getLogger("ai-rag-core.forecast")

_PROMPTS_DIR = Path(__file__).parent.parent / "prompts"


def _load_template(filename: str) -> str:
    return (_PROMPTS_DIR / filename).read_text(encoding="utf-8").strip()


def _compute_signals(timeseries: list[dict]) -> tuple[list[ForecastSignal], dict]:
    """
    Tính các tín hiệu thống kê từ time-series.
    Trả về (signals, current_status).
    """
    signals: list[ForecastSignal] = []

    if not timeseries:
        return signals, {}

    import statistics

    growth_rates = [
        float(r["growth_rate"]) for r in timeseries if r.get("growth_rate") is not None
    ]
    mom_values = [
        float(r["mom_growth"]) for r in timeseries if r.get("mom_growth") is not None
    ]
    job_counts = [int(r["job_count"]) for r in timeseries if r.get("job_count") is not None]

    # Linear trend (slope của growth_rate)
    if len(growth_rates) >= 3:
        try:
            import numpy as np

            x = list(range(len(growth_rates)))
            slope = float(np.polyfit(x, growth_rates, 1)[0])
            signals.append(ForecastSignal(
                signal="Xu hướng tăng trưởng tuyến tính (linear slope)",
                value=round(slope, 3),
                weight=0.35,
            ))
        except Exception:
            pass

    # Momentum (MoM 3 tháng gần nhất)
    if len(mom_values) >= 3:
        momentum = statistics.mean(mom_values[-3:])
        signals.append(ForecastSignal(
            signal="Momentum MoM trung bình (3 tháng gần nhất)",
            value=round(momentum, 2),
            weight=0.30,
        ))

    # Volatility
    if len(growth_rates) >= 3:
        vol = statistics.stdev(growth_rates)
        signals.append(ForecastSignal(
            signal="Độ biến động tăng trưởng (volatility)",
            value=round(vol, 2),
            weight=0.10,
        ))

    # Job demand trend
    if len(job_counts) >= 2:
        job_change = job_counts[-1] - job_counts[0]
        signals.append(ForecastSignal(
            signal="Thay đổi số việc làm trong kỳ",
            value=job_change,
            weight=0.25,
        ))

    latest = timeseries[-1]
    current_status = {
        "job_count":     latest.get("job_count"),
        "article_count": latest.get("article_count"),
        "growth_rate":   latest.get("growth_rate"),
        "mom_growth":    latest.get("mom_growth"),
        "month":         str(latest.get("month") or "")[:7],
    }

    return signals, current_status


async def _get_sentiment_signal(tech_name: str) -> ForecastSignal | None:
    """Neo4j: article sentiment 3 tháng gần nhất."""
    try:
        rows = await run_query(
            """
            MATCH (t:Technology)<-[:MENTIONS]-(a:Article)
            WHERE toLower(t.name) = toLower($name)
              AND a.published_date >= date() - duration('P3M')
            RETURN count(a) AS recent_count,
                   avg(a.sentiment_score) AS avg_sentiment
            """,
            {"name": tech_name},
        )
        if rows and rows[0].get("recent_count"):
            count = rows[0]["recent_count"]
            sentiment = rows[0].get("avg_sentiment") or 0.0
            return ForecastSignal(
                signal=f"Số bài viết gần đây (3 tháng): {count}, sentiment trung bình: {sentiment:.2f}",
                value=float(sentiment),
                weight=0.20,
            )
    except Exception as e:
        logger.warning("Sentiment signal failed for %s: %s", tech_name, e)
    return None


async def _llm_synthesize(
    tech: str,
    signals: list[ForecastSignal],
    current_status: dict,
    trend_data: list[dict],
    horizon_months: int,
) -> tuple[str, float, str]:
    """
    LLM tổng hợp signals → (predicted_direction, confidence, reasoning).
    """
    template = _load_template("forecast_template.txt")

    signals_text = "\n".join(
        f"- {s.signal}: {s.value} (trọng số {s.weight})" for s in signals
    )
    trend_text = "\n".join(
        f"  {str(r.get('month', ''))[:7]}: {r.get('job_count', 0)} việc, "
        f"tăng trưởng {r.get('growth_rate') or 0:+.1f}%"
        for r in trend_data[-6:]
    )

    prompt = template.format(
        technology=tech,
        horizon_months=horizon_months,
        current_status=str(current_status),
        signals=signals_text,
        trend_data=trend_text,
    )

    messages = [
        {
            "role": "system",
            "content": (
                "Bạn là chuyên gia phân tích xu hướng công nghệ tại Việt Nam. "
                "Dựa hoàn toàn vào dữ liệu được cung cấp, không bịa thêm số liệu. "
                "Trả về JSON: {\"direction\": \"growing|stable|declining\", "
                "\"confidence\": 0.0-1.0, \"reasoning\": \"...\"}"
            ),
        },
        {"role": "user", "content": prompt},
    ]

    try:
        raw = await generate(messages)
        import json, re

        match = re.search(r"\{.*\}", raw, re.DOTALL)
        if match:
            parsed = json.loads(match.group())
            return (
                parsed.get("direction", "stable"),
                float(parsed.get("confidence", 0.5)),
                parsed.get("reasoning", ""),
            )
    except Exception as e:
        logger.warning("LLM forecast synthesis failed: %s", e)

    return "stable", 0.5, "Không đủ dữ liệu để dự báo."


async def handle(req: ForecastRequest) -> ForecastResponse:
    # 1. Lấy time-series từ PostgreSQL
    timeseries = await sql_tech_timeseries(req.technology, months=req.horizon_months * 2)

    if not timeseries:
        return ForecastResponse(
            technology=req.technology,
            current_status={},
            predicted_direction="stable",
            confidence=0.0,
            reasoning="Không tìm thấy dữ liệu analytics cho công nghệ này.",
            signals=[],
            trend_data=[],
        )

    # 2. Tính signals thống kê
    signals, current_status = _compute_signals(timeseries)

    # 3. Neo4j sentiment signal
    sentiment_signal = await _get_sentiment_signal(req.technology)
    if sentiment_signal:
        signals.append(sentiment_signal)

    # 4. LLM synthesis
    trend_data = [
        {
            "month":        str(r.get("month") or "")[:7],
            "job_count":    r.get("job_count"),
            "article_count": r.get("article_count"),
            "growth_rate":  r.get("growth_rate"),
            "mom_growth":   r.get("mom_growth"),
        }
        for r in timeseries
    ]

    direction, confidence, reasoning = await _llm_synthesize(
        req.technology, signals, current_status, trend_data, req.horizon_months
    )

    return ForecastResponse(
        technology=req.technology,
        current_status=current_status,
        predicted_direction=direction,
        confidence=round(confidence, 3),
        reasoning=reasoning,
        signals=signals,
        trend_data=trend_data,
    )
