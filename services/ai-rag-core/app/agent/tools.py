"""
LangChain tools cho AI Agent — wrapper mỏng trên các service đã có.
Mỗi tool là 1 async function với docstring mô tả để LLM tự quyết định khi nào dùng.
"""
import json

from langchain_core.tools import tool


@tool
async def search_knowledge(query: str) -> str:
    """
    Tìm kiếm thông tin về công nghệ IT trong knowledge base của TechRadar VN.
    Kết hợp bài viết, dữ liệu tuyển dụng và analytics để trả lời câu hỏi.
    Dùng khi cần thông tin tổng quan, giải thích, hoặc so sánh công nghệ.
    """
    from app.core.pipeline import answer
    result = await answer(query=query)
    return result.get("answer", "Không tìm thấy thông tin.")


@tool
async def recommend_technologies(current_techs: str, limit: int = 5) -> str:
    """
    Gợi ý các công nghệ nên học tiếp dựa trên công nghệ hiện tại của user.
    current_techs: danh sách công nghệ phân cách bằng dấu phẩy, VD: 'React, TypeScript, Node.js'
    limit: số lượng gợi ý (1-10, mặc định 5)
    Dùng khi user hỏi 'tôi nên học gì tiếp theo' hoặc 'công nghệ nào liên quan đến X'.
    """
    from app.services import recommend_service
    from app.api.schemas import RecommendRequest

    techs = [t.strip() for t in current_techs.split(",") if t.strip()]
    req = RecommendRequest(current_techs=techs, limit=min(limit, 10))
    # db=None → bỏ qua user profile, dùng current_techs trực tiếp
    resp = await recommend_service.handle(req, db=None)
    items = [
        f"- {r.tech_name}: {r.reason} (tăng trưởng: {r.growth_rate or 'N/A'}%)"
        for r in resp.recommendations
    ]
    return f"Gợi ý cho {current_techs}:\n" + "\n".join(items) if items else "Không có gợi ý."


@tool
async def forecast_technology(technology: str, horizon_months: int = 6) -> str:
    """
    Dự báo xu hướng của 1 công nghệ trong N tháng tới dựa trên time-series và signals.
    technology: tên công nghệ cụ thể, VD: 'React', 'Kubernetes', 'Python'
    horizon_months: số tháng dự báo (1-24, mặc định 6)
    Dùng khi user hỏi về tương lai, xu hướng, hay nên đầu tư vào công nghệ nào.
    """
    from app.services import forecast_service
    from app.api.schemas import ForecastRequest

    req = ForecastRequest(technology=technology, horizon_months=min(horizon_months, 24))
    resp = await forecast_service.handle(req)
    signals_text = ", ".join(
        f"{s.signal}={s.value}" for s in resp.signals[:3]
    )
    return (
        f"{resp.technology}: {resp.predicted_direction.upper()} "
        f"(confidence {resp.confidence:.0%})\n"
        f"Lý do: {resp.reasoning}\n"
        f"Signals: {signals_text}"
    )


@tool
async def summarize_technology(tech_name: str, period: str = "") -> str:
    """
    Tóm tắt các bài viết và tin tức về 1 công nghệ trong khoảng thời gian nhất định.
    tech_name: tên công nghệ, VD: 'Kubernetes', 'GPT-4', 'Rust'
    period: kỳ thời gian, VD: '2024-Q4', '2024-12', hoặc để trống = 3 tháng gần nhất
    Dùng khi user muốn biết gần đây có gì mới về 1 công nghệ cụ thể.
    """
    from app.services import summarize_service
    from app.api.schemas import SummarizeRequest

    req = SummarizeRequest(
        tech_name=tech_name,
        period=period if period else None,
        format="bullet",
    )
    resp = await summarize_service.handle(req)
    key_pts = "\n".join(f"• {pt}" for pt in resp.key_points[:5])
    return (
        f"Tóm tắt {resp.tech_name} ({resp.period}, {resp.sources_used} nguồn):\n"
        f"{resp.summary[:400]}\n\nĐiểm chính:\n{key_pts}"
    )


ALL_TOOLS = [search_knowledge, recommend_technologies, forecast_technology, summarize_technology]
