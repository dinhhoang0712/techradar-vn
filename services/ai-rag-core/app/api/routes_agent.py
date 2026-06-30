from fastapi import APIRouter, Depends

from app.api.schemas import AgentRequest, AgentResponse, AgentStep
from app.api.security import require_internal_auth
from app.agent.executor import run_agent

router = APIRouter(
    prefix="/agent",
    tags=["agent"],
    dependencies=[Depends(require_internal_auth)],
)


@router.post("", response_model=AgentResponse)
async def agent(req: AgentRequest) -> AgentResponse:
    """
    AI Agent tự động chọn tool phù hợp để trả lời câu hỏi.

    Agent có thể kết hợp nhiều bước:
    - search_knowledge: RAG search bài viết + job + analytics
    - recommend_technologies: gợi ý công nghệ liên quan
    - forecast_technology: dự báo xu hướng
    - summarize_technology: tóm tắt tin tức

    Trả về câu trả lời tổng hợp + danh sách các bước đã thực hiện.
    """
    result = await run_agent(req.query)
    return AgentResponse(
        answer=result["answer"],
        steps=[AgentStep(**s) for s in result["steps"]],
    )
