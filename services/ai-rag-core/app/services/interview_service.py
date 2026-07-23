"""
Mock Interview Service — AI đóng vai người phỏng vấn kỹ thuật.
  1. Lượt đầu: tìm 1 tin tuyển dụng thật khớp vai trò (+ công ty nếu có) trong Neo4j
     làm ngữ cảnh, LLM sinh câu hỏi mở đầu.
  2. Các lượt giữa: LLM nhận xét câu trả lời vừa rồi + hỏi câu tiếp theo (1 lệnh gọi,
     parse theo delimiter cố định).
  3. Lượt cuối (đủ MAX_TURNS): LLM chấm điểm tổng kết + nhận xét.

Stateless: client gửi kèm toàn bộ lịch sử câu hỏi-trả lời mỗi lần gọi, không lưu
phiên phỏng vấn ở đây (khác `/chat`, giống `/career`, `/recommend`).
"""

import logging
import re
from pathlib import Path

from app.api.schemas import InterviewFinalSummary, InterviewRequest, InterviewResponse, InterviewTurn
from app.core.generator import generate
from app.db.graph_queries import JOBS_BY_TITLE, JOBS_BY_TITLE_AND_COMPANY
from app.db.neo4j_client import run_query

logger = logging.getLogger("ai-rag-core.interview")

_PROMPTS_DIR = Path(__file__).parent.parent / "prompts"
MAX_TURNS = 5

_SCORE_RE = re.compile(r"SCORE:\s*(\d{1,2})\s*/\s*10", re.IGNORECASE)


def _load_template(filename: str) -> str:
    return (_PROMPTS_DIR / filename).read_text(encoding="utf-8").strip()


async def _find_job_context(target_role: str, target_company: str | None) -> str:
    keywords = [target_role.lower()]
    rows = []
    try:
        if target_company:
            rows = await run_query(
                JOBS_BY_TITLE_AND_COMPANY,
                {"keywords": keywords, "company": target_company},
            )
        if not rows:
            rows = await run_query(JOBS_BY_TITLE, {"keywords": keywords})
    except Exception as e:
        logger.warning("Job context lookup failed for role=%s company=%s: %s", target_role, target_company, e)

    if not rows:
        return "(Không tìm thấy tin tuyển dụng cụ thể — hỏi dựa trên kiến thức chung của vị trí này.)"

    job = rows[0]
    parts = [f"Vị trí: {job.get('title') or target_role}"]
    if job.get("company"):
        parts.append(f"Công ty: {job['company']}")
    if job.get("requirement"):
        parts.append(f"Yêu cầu: {job['requirement']}")
    if job.get("description"):
        parts.append(f"Mô tả: {job['description']}")
    if job.get("technology"):
        parts.append(f"Công nghệ liên quan: {', '.join(job['technology'])}")
    return "\n".join(parts)


def _format_transcript(history: list[InterviewTurn]) -> str:
    if not history:
        return "(chưa có)"
    lines = []
    for i, turn in enumerate(history, start=1):
        lines.append(f"Câu hỏi {i}: {turn.question}")
        lines.append(f"Trả lời {i}: {turn.answer}")
    return "\n".join(lines)


def _split_turn_response(raw: str) -> tuple[str, str]:
    """Parse the fixed ---FEEDBACK---/---QUESTION--- format; fall back to treating
    the whole reply as the next question if the model didn't follow it."""
    if "---FEEDBACK---" in raw and "---QUESTION---" in raw:
        _, rest = raw.split("---FEEDBACK---", 1)
        feedback_part, question_part = rest.split("---QUESTION---", 1)
        return feedback_part.strip(), question_part.strip()
    return "", raw.strip()


def _extract_score(raw: str) -> tuple[int, str]:
    match = _SCORE_RE.search(raw)
    if not match:
        return 5, raw.strip()
    score = max(0, min(10, int(match.group(1))))
    summary = _SCORE_RE.sub("", raw).strip()
    return score, summary


async def handle(req: InterviewRequest) -> InterviewResponse:
    target_role = req.target_role.strip()
    target_company = (req.target_company or "").strip() or "một công ty công nghệ tại Việt Nam"
    history = req.history

    system_message = {
        "role": "system",
        "content": (
            f"Bạn là người phỏng vấn kỹ thuật tại {target_company}, đang phỏng vấn ứng viên "
            f"cho vị trí {target_role}. Giữ giọng chuyên nghiệp, thẳng thắn, bằng tiếng Việt."
        ),
    }

    # ── Lượt đầu tiên: chưa có lịch sử, chỉ cần sinh câu hỏi mở đầu ──
    if len(history) == 0:
        job_context = await _find_job_context(target_role, req.target_company)
        prompt = _load_template("interview_opening_template.txt").format(
            target_role=target_role,
            target_company=target_company,
            job_context=job_context,
        )
        question = await generate([system_message, {"role": "user", "content": prompt}])
        return InterviewResponse(
            next_question=question.strip(),
            feedback_on_last_answer=None,
            is_final=False,
            turn=1,
        )

    transcript_so_far = _format_transcript(history)
    latest_answer = history[-1].answer

    # ── Giữa buổi: nhận xét câu trả lời vừa rồi + hỏi câu tiếp theo ──
    if len(history) < MAX_TURNS:
        prompt = _load_template("interview_turn_template.txt").format(
            target_role=target_role,
            target_company=target_company,
            transcript_so_far=transcript_so_far,
            latest_answer=latest_answer,
        )
        raw = await generate([system_message, {"role": "user", "content": prompt}])
        feedback, question = _split_turn_response(raw)
        return InterviewResponse(
            next_question=question,
            feedback_on_last_answer=feedback,
            is_final=False,
            turn=len(history) + 1,
        )

    # ── Đủ số lượt: chấm điểm tổng kết ──
    prompt = _load_template("interview_final_template.txt").format(
        target_role=target_role,
        target_company=target_company,
        transcript_so_far=transcript_so_far,
        latest_answer=latest_answer,
    )
    raw = await generate([system_message, {"role": "user", "content": prompt}])
    score, summary = _extract_score(raw)
    return InterviewResponse(
        next_question=None,
        feedback_on_last_answer=None,
        is_final=True,
        turn=len(history),
        final_summary=InterviewFinalSummary(score=score, summary=summary),
    )
