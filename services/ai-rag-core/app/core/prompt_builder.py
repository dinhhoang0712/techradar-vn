from pathlib import Path

_PROMPTS_DIR = Path(__file__).parent.parent / "prompts"


def _load(filename: str) -> str:
    return (_PROMPTS_DIR / filename).read_text(encoding="utf-8").strip()


def format_analytics_line(tech: str, rows: list[dict]) -> str:
    """Format 1 nhóm row tech_analytics (đã group theo technology_name) thành 1 dòng text."""
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
    return ", ".join(parts)


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

    lines = [format_analytics_line(tech, rows) for tech, rows in by_tech.items()]
    return "\n".join(lines)


def build_messages(
    query: str,
    articles: list[dict],
    graph_data: dict | None = None,
    user_block: str = "",
    low_confidence: bool = False,
    sql_data: list[dict] | None = None,
    history: list[dict] | None = None,
    subgraph_triples: list[dict] | None = None,
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
    subgraph_triples: list[dict] — kết quả từ retriever_graph_expand.expand_subgraph()
                                  (subject/predicate/object/hop), rỗng nếu Strategy Selector
                                  không bật graph expansion cho câu hỏi này
    Trả về: [system, ...history_turns..., user_with_rag_context]
    """
    context_block = _build_context_block(articles, low_confidence=low_confidence)
    job_context_block = _build_job_context_block(graph_data or {})
    analytics_block = _build_analytics_block(sql_data or [])
    subgraph_block = _build_subgraph_block(subgraph_triples or [])

    rag_template = _load("rag_template.txt")
    user_content = rag_template.format(
        context=context_block,
        job_context=job_context_block,
        analytics_block=analytics_block,
        subgraph_block=subgraph_block,
        user_block=user_block,
        query=query,
    )

    messages: list[dict] = [{"role": "system", "content": _load("system_prompt.txt")}]
    for turn in history or []:
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
        title = article.get("title") or "Không có tiêu đề"
        content = article.get("content") or ""
        date = article.get("published_date") or ""

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
            "nếu không đủ, hãy nói rõ thay vì suy diễn.\n\n" + result
        )

    return result


def format_job_line(j: dict) -> str:
    """Format 1 job dict (từ graph_search()) thành text nhiều dòng."""
    title = j.get("title") or "N/A"
    tech = j.get("technology") or ""
    company = j.get("company") or "N/A"
    location = j.get("location") or ""
    salary = j.get("salary") or ""
    description = j.get("description") or ""
    requirement = j.get("requirement") or ""
    benefit = j.get("benefit") or ""

    salary_str = f", lương {salary}" if salary else ""
    tech_str = f" (yêu cầu: {tech})" if tech else ""
    location_str = f", {location}" if location else ""
    line = f"  - {title}{tech_str} tại {company}{location_str}{salary_str}"
    if description:
        line += f"\n    Mô tả: {description[:200]}..." if len(description) > 200 else f"\n    Mô tả: {description}"
    if requirement:
        line += (
            f"\n    Yêu cầu: {requirement[:200]}..." if len(requirement) > 200 else f"\n    Yêu cầu: {requirement}"
        )
    if benefit:
        line += f"\n    Phúc lợi: {benefit[:150]}..." if len(benefit) > 150 else f"\n    Phúc lợi: {benefit}"
    return line


def format_company_line(c: dict) -> str:
    """Format 1 company dict (từ graph_search()) thành 1 dòng text."""
    name = c.get("name") or "N/A"
    tech = c.get("technology") or ""
    industry = c.get("industry") or ""
    location = c.get("location") or ""
    size = c.get("size") or ""
    rating = c.get("rating")

    meta = ", ".join(filter(None, [industry, location, size]))
    rating_str = f", rating {rating}" if rating else ""
    tech_str = f" (dùng {tech})" if tech else ""
    return f"  - {name}{tech_str}: {meta}{rating_str}"


def _build_job_context_block(graph_data: dict) -> str:
    """Định dạng dữ liệu tuyển dụng từ graph_search() thành text cho prompt."""
    jobs = graph_data.get("jobs", [])
    companies = graph_data.get("companies", [])
    related_tech = graph_data.get("related_tech", [])

    if not jobs and not companies and not related_tech:
        return "(Không có dữ liệu tuyển dụng liên quan.)"

    parts = []

    if jobs:
        parts.append("Tin tuyển dụng:")
        parts.extend(format_job_line(j) for j in jobs)

    if companies:
        parts.append("\nCông ty đang dùng:")
        parts.extend(format_company_line(c) for c in companies)

    if related_tech:
        techs = list({r["related_tech"] for r in related_tech})
        parts.append(f"\nCông nghệ liên quan: {', '.join(techs)}")

    return "\n".join(parts)


_SUBGRAPH_PREDICATE_PHRASES = {
    "REQUIRES": "yêu cầu",
    "USES": "sử dụng",
    "POSTED_BY": "được đăng bởi",
    "HIRES_FOR": "được đăng bởi",
    "MENTIONS": "được nhắc tới trong",
    "RELATED_TO": "liên quan tới",
}


def _build_subgraph_block(triples: list[dict]) -> str:
    """
    Định dạng triple (subject/predicate/object/hop) từ retriever_graph_expand.expand_subgraph()
    thành text tiếng Việt cho prompt — nhóm theo hop-distance thay vì liệt kê phẳng, để LLM
    thấy được "liên quan trực tiếp" khác với "mở rộng nhiều bước" thay vì 1 túi sự kiện không
    thứ tự.
    """
    if not triples:
        return "(Không có dữ liệu đồ thị mở rộng.)"

    by_hop: dict[int, list[dict]] = {}
    for t in triples:
        by_hop.setdefault(t.get("hop") or 1, []).append(t)

    parts = []
    for hop in sorted(by_hop):
        label = "Liên quan trực tiếp" if hop == 1 else f"Mở rộng {hop} bước"
        parts.append(f"{label}:")
        for t in by_hop[hop]:
            phrase = _SUBGRAPH_PREDICATE_PHRASES.get(t.get("predicate") or "", "liên quan tới")
            parts.append(f"  {t.get('subject')} {phrase} {t.get('object')}")

    return "\n".join(parts)
