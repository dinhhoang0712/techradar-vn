"""
RAGAS-style evaluation scorer — LLM judge faithfulness.
Log kết quả vào mlflow.db (file đã tồn tại tại services/ai-rag-core/mlflow.db).

Chỉ chạy khi settings.eval_enabled = True (tắt mặc định để không tăng latency).
"""
import logging
import re

from app.core.generator import generate

logger = logging.getLogger("ai-rag-core.evaluation")


async def score_faithfulness(
    question: str,
    answer: str,
    contexts: list[str],
) -> float:
    """
    LLM judge: câu trả lời có trung thực với context không?
    Trả về float 0.0–1.0. Lỗi → trả về -1.0 (không log).
    """
    context_text = "\n\n".join(f"[{i+1}] {c[:500]}" for i, c in enumerate(contexts[:5]))
    messages = [
        {
            "role": "system",
            "content": (
                "Bạn là judge đánh giá chất lượng AI. "
                "Xem xét câu trả lời có dựa hoàn toàn vào context không, hay có thông tin bịa. "
                "Trả về CHỈ một số thập phân từ 0.0 đến 1.0 (1.0 = hoàn toàn trung thực)."
            ),
        },
        {
            "role": "user",
            "content": (
                f"Câu hỏi: {question}\n\n"
                f"Context:\n{context_text}\n\n"
                f"Câu trả lời: {answer}\n\n"
                "Faithfulness score (0.0-1.0):"
            ),
        },
    ]
    try:
        raw = await generate(messages)
        match = re.search(r"\b(0\.\d+|1\.0|0|1)\b", raw.strip())
        if match:
            return float(match.group())
    except Exception as e:
        logger.warning("Faithfulness scoring failed: %s", e)
    return -1.0


def log_to_mlflow(
    question: str,
    faithfulness: float,
    latency_ms: float,
    tokens_used: int,
    model: str,
) -> None:
    """Log metrics vào mlflow.db (experiment_id=1 đã tồn tại)."""
    try:
        import mlflow

        mlflow.set_tracking_uri("sqlite:///mlflow.db")
        with mlflow.start_run(experiment_id="1", run_name="rag_eval", nested=True):
            if faithfulness >= 0:
                mlflow.log_metric("faithfulness", faithfulness)
            mlflow.log_metric("latency_ms", latency_ms)
            mlflow.log_metric("tokens_used", tokens_used)
            mlflow.log_param("model", model)
            mlflow.log_param("question_len", len(question))
    except Exception as e:
        logger.warning("MLflow logging failed: %s", e)


async def evaluate(
    question: str,
    answer: str,
    contexts: list[str],
    latency_ms: float = 0.0,
    tokens_used: int = 0,
    model: str = "",
) -> dict:
    """
    Đánh giá + log. Gọi từ pipeline sau khi có LLM response.
    Chỉ active khi settings.eval_enabled = True.
    """
    faithfulness = await score_faithfulness(question, answer, contexts)
    log_to_mlflow(question, faithfulness, latency_ms, tokens_used, model)
    return {"faithfulness": faithfulness}
