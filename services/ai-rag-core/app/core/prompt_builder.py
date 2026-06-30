from pathlib import Path

_PROMPTS_DIR = Path(__file__).parent.parent / "prompts"


def _load(filename: str) -> str:
    return (_PROMPTS_DIR / filename).read_text(encoding="utf-8").strip()


def _build_analytics_block(sql_data: list[dict]) -> str:
    """
    Format dữ liệu tech_analytics thành text cho LLM.
    Nhóm theo technology_name, lấy tháng gần nhất + xu hướng.
    """
    if not sql_data:
        return "(Không có dữ liệu analytics.)"

    by_tech: dict[str, list[dict]] = {}
    for row in sql_data:
        name = row.get("technology_name") or "Unknown"
        by_tech.setdefault(name, []).append(row)

    lines = []
    for tech, rows in by_tech.items():
        rows_sorted = sorted(rows, key=lambda r: str(r.get("month") or ""), reverse=True)
        latest = rows_sorted[0]
        job_count = latest.get("job_count") or 0
        article_count = latest.get("article_count") or 0
        mom = latest.get("mom_growth")
        yoy = latest.get("yoy_growth")
        growth = latest.get("growth_rate")
        month = str(latest.get("month") or "")[:7]

        parts = [f"{tech} ({month}): {job_count} việc làm, {article_count} bài viết"]
        if mom is not None:
            parts.append(f"MoM {mom:+.1f}%")
        if yoy is not None:
            parts.append(f"YoY {yoy:+.1f}%")
        if growth is not None and mom is None:
            parts.append(f"tăng trưởng {growth:+.1f}%")
        lines.append(", ".join(parts))

    return "\n".join(lines)


def build_messages(
    query: str,
    articles: list[dict],
    graph_data: dict | None = None,
    user_block: str = "",
    low_confidence: bool = False,
    sql_data: list[dict] | None = None,
    history: list[dict] | None = None,
) -> list[dict]:
    """
    Ghép context từ article + graph data + user profile + lịch sử hội thoại thành messages cho LLM.

    articles:        list[dict] — top-5 article sau rerank
    graph_data:      dict       — kết quả từ graph_search() (jobs, companies, related_tech)
    user_block:      str        — output của retriever_user.build_user_block() (rỗng nếu anonymous)
    low_confidence:  bool       — True khi articles dưới threshold (query mơ hồ, không có entity)
                                  → thêm cảnh báo vào prompt để LLM không suy diễn bừa
    sql_data:        list[dict] — kết quả từ retriever_sql.sql_analytics_search()
    history:         list[dict] — lịch sử hội thoại [{"role": "user"|"assistant", "content": ...}]
                                  inject làm multi-turn context trước câu hỏi hiện tại
    Trả về: [system, ...history_turns..., user_with_rag_context]
    """
    context_block      = _build_context_block(articles, low_confidence=low_confidence)
    job_context_block  = _build_job_context_block(graph_data or {})
    analytics_block    = _build_analytics_block(sql_data or [])

    rag_template = _load("rag_template.txt")
    user_content = rag_template.format(
        context=context_block,
        job_context=job_context_block,
        analytics_block=analytics_block,
        user_block=user_block,
        query=query,
    )

    messages: list[dict] = [{"role": "system", "content": _load("system_prompt.txt")}]
    for turn in (history or []):
        messages.append({"role": turn["role"], "content": turn["content"]})
    messages.append({"role": "user", "content": user_content})
    return messages


def _build_context_block(articles: list[dict], low_confidence: bool = False) -> str:
    """Định dạng article thành block đánh số [1], [2], ... cho LLM trích dẫn.

    Khi low_confidence=True, thêm cảnh báo để LLM không suy diễn từ bài không liên quan.
    """
    if not articles:
        return "(Không có bài viết liên quan nào được tìm thấy.)"

    blocks = []
    for i, article in enumerate(articles, start=1):
        title   = article.get("title") or "Không có tiêu đề"
        content = article.get("content") or ""
        date    = article.get("published_date") or ""

        if len(content) > 800:
            content = content[:800] + "..."

        date_str = f" ({str(date)[:10]})" if date else ""
        blocks.append(f"[{i}] {title}{date_str}\n{content}")

    result = "\n\n".join(blocks)

    if low_confidence:
        result = (
            "⚠️ Lưu ý: Các bài viết dưới đây có độ liên quan THẤP với câu hỏi "
            "(không tìm thấy bài khớp trực tiếp). "
            "Chỉ sử dụng nếu có thông tin thực sự liên quan; "
            "nếu không đủ, hãy nói rõ thay vì suy diễn.\n\n"
            + result
        )

    return result


def _build_job_context_block(graph_data: dict) -> str:
    """Định dạng dữ liệu tuyển dụng từ graph_search() thành text cho prompt."""
    jobs         = graph_data.get("jobs", [])
    companies    = graph_data.get("companies", [])
    related_tech = graph_data.get("related_tech", [])

    if not jobs and not companies and not related_tech:
        return "(Không có dữ liệu tuyển dụng liên quan.)"

    parts = []

    if jobs:
        parts.append("Tin tuyển dụng:")
        for j in jobs:
            title       = j.get("title") or "N/A"
            tech        = j.get("technology") or ""
            company     = j.get("company") or "N/A"
            location    = j.get("location") or ""
            salary      = j.get("salary") or ""
            description = j.get("description") or ""
            requirement = j.get("requirement") or ""
            benefit     = j.get("benefit") or ""

            salary_str  = f", lương {salary}" if salary else ""
            tech_str    = f" (yêu cầu: {tech})" if tech else ""
            location_str = f", {location}" if location else ""
            line = f"  - {title}{tech_str} tại {company}{location_str}{salary_str}"
            if description:
                line += f"\n    Mô tả: {description[:200]}..." if len(description) > 200 else f"\n    Mô tả: {description}"
            if requirement:
                line += f"\n    Yêu cầu: {requirement[:200]}..." if len(requirement) > 200 else f"\n    Yêu cầu: {requirement}"
            if benefit:
                line += f"\n    Phúc lợi: {benefit[:150]}..." if len(benefit) > 150 else f"\n    Phúc lợi: {benefit}"
            parts.append(line)

    if companies:
        parts.append("\nCông ty đang dùng:")
        for c in companies:
            name     = c.get("name") or "N/A"
            tech     = c.get("technology") or ""
            industry = c.get("industry") or ""
            location = c.get("location") or ""
            size     = c.get("size") or ""
            rating   = c.get("rating")

            meta = ", ".join(filter(None, [industry, location, size]))
            rating_str = f", rating {rating}" if rating else ""
            tech_str   = f" (dùng {tech})" if tech else ""
            parts.append(f"  - {name}{tech_str}: {meta}{rating_str}")

    if related_tech:
        techs = list({r["related_tech"] for r in related_tech})
        parts.append(f"\nCông nghệ liên quan: {', '.join(techs)}")

    return "\n".join(parts)
