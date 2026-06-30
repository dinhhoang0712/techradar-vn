"""
AI Agent Executor — LangChain tool-calling agent.
Nhận câu hỏi của user, tự quyết định gọi tool nào, tổng hợp câu trả lời cuối cùng.
"""
import logging

from langchain.agents import AgentExecutor, create_tool_calling_agent
from langchain_core.prompts import ChatPromptTemplate, MessagesPlaceholder

from app.agent.tools import ALL_TOOLS
from app.core.generator import get_llm

logger = logging.getLogger("ai-rag-core.agent")

_SYSTEM_PROMPT = """Bạn là AI agent chuyên phân tích xu hướng công nghệ IT Việt Nam, được tích hợp trong TechRadar VN.

Khả năng của bạn:
- search_knowledge: tìm kiếm thông tin từ knowledge base (bài viết, job, analytics)
- recommend_technologies: gợi ý công nghệ nên học tiếp
- forecast_technology: dự báo xu hướng công nghệ
- summarize_technology: tóm tắt tin tức về 1 công nghệ

Nguyên tắc:
1. Luôn dùng ít nhất 1 tool để có dữ liệu thực trước khi trả lời.
2. Kết hợp nhiều tool nếu cần thiết (VD: forecast + search để trả lời câu hỏi toàn diện).
3. Trả lời bằng tiếng Việt, ngắn gọn và có dữ liệu cụ thể.
4. Nếu tool không trả về đủ dữ liệu, thành thật nói rõ giới hạn."""


def _build_executor() -> AgentExecutor:
    llm = get_llm()
    prompt = ChatPromptTemplate.from_messages([
        ("system", _SYSTEM_PROMPT),
        ("human", "{input}"),
        MessagesPlaceholder("agent_scratchpad"),
    ])
    agent = create_tool_calling_agent(llm, ALL_TOOLS, prompt)
    return AgentExecutor(
        agent=agent,
        tools=ALL_TOOLS,
        max_iterations=5,
        early_stopping_method="generate",
        verbose=False,
        return_intermediate_steps=True,
    )


async def run_agent(query: str) -> dict:
    """
    Chạy agent với câu hỏi đầu vào.
    Trả về {"answer": str, "steps": list[dict]}.
    """
    executor = _build_executor()
    try:
        result = await executor.ainvoke({"input": query})
    except Exception as e:
        logger.error("Agent execution failed: %s", e)
        return {"answer": f"Agent gặp lỗi: {e}", "steps": []}

    steps = []
    for action, observation in result.get("intermediate_steps", []):
        steps.append({
            "tool":   action.tool,
            "input":  str(action.tool_input),
            "output": str(observation)[:600],
        })

    return {
        "answer": result.get("output", ""),
        "steps":  steps,
    }
