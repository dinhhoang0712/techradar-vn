"""
Đánh giá RAG pipeline bằng RAGAS (Faithfulness + Answer Relevancy).
Judge: GPT-4o-mini (không cần ground truth).
Kết quả log vào MLflow.

Cách chạy (từ thư mục src/ai-rag-core/):
    python -m scripts.evaluate_rag
"""

import asyncio
import sys
import time
from pathlib import Path

sys.path.insert(0, str(Path(__file__).parent.parent))

import mlflow
from datasets import Dataset
import warnings
warnings.filterwarnings("ignore", category=DeprecationWarning)

from ragas import evaluate
from ragas.metrics._faithfulness import faithfulness as faithfulness_metric
from ragas.metrics._answer_relevance import answer_relevancy as answer_relevancy_metric
from ragas.llms import LangchainLLMWrapper
from ragas.embeddings import LangchainEmbeddingsWrapper
from langchain_openai import ChatOpenAI, OpenAIEmbeddings

from app.config import get_settings
from app.core.pipeline import answer as rag_answer

# ── Bộ câu hỏi test ──────────────────────────────────────────────────────────

TEST_QUERIES = [
    "Lương kỹ sư phần mềm ở Việt Nam hiện tại ra sao?",
    "Công việc Python developer lương bao nhiêu?",
    "Tôi muốn tìm việc Data Engineer dùng Kafka và Spark",
    "FPT tuyển kỹ sư phần mềm không?",
    "Xu hướng AI trong ngành IT Việt Nam năm 2025 là gì?",
    "Shopee đang tuyển vị trí gì?",
    "React developer cần kỹ năng gì?",
    "Lương DevOps engineer ở Hà Nội bao nhiêu?",
]


# ── Chạy pipeline, thu thập data ─────────────────────────────────────────────

async def run_pipeline_for_eval(queries: list[str]) -> list[dict]:
    """Chạy từng query qua RAG pipeline, lấy answer + contexts."""
    rows = []
    for i, query in enumerate(queries, 1):
        print(f"  [{i}/{len(queries)}] {query[:60]}...")
        t0 = time.time()
        try:
            result = await rag_answer(query)
            elapsed = time.time() - t0

            # 1. Article contexts
            contexts = []
            for src in result.get("sources", []):
                title   = src.get("title") or ""
                content = src.get("content") or ""
                if title or content:
                    contexts.append(f"{title}\n{content}".strip())

            # 2. Job context (structured data gửi vào prompt — nguồn chính cho câu hỏi tuyển dụng)
            job_ctx = result.get("job_context", "")
            if job_ctx and job_ctx != "(Không có dữ liệu tuyển dụng liên quan.)":
                contexts.append(job_ctx)

            if not contexts:
                contexts = ["(không có context)"]

            rows.append({
                "question":   query,
                "answer":     result.get("answer", ""),
                "contexts":   contexts,
                "latency_ms": int(elapsed * 1000),
            })
        except Exception as e:
            print(f"     ⚠ Lỗi: {e}")
            rows.append({
                "question":   query,
                "answer":     "",
                "contexts":   ["(lỗi pipeline)"],
                "latency_ms": 0,
            })
    return rows


# ── Main ──────────────────────────────────────────────────────────────────────

async def main() -> None:
    settings = get_settings()

    if not settings.openai_api_key:
        print("Lỗi: OPENAI_API_KEY chưa được đặt trong .env")
        return

    print("=" * 60)
    print("RAGAS Evaluation — GPT-4o-mini judge")
    print("=" * 60)

    # 1. Chạy pipeline lấy dữ liệu
    print(f"\nChạy {len(TEST_QUERIES)} queries qua RAG pipeline...")
    rows = await run_pipeline_for_eval(TEST_QUERIES)

    # Lọc bỏ câu trả lời rỗng
    valid_rows = [r for r in rows if r["answer"] and r["answer"] != "Tôi không tìm thấy thông tin liên quan trong dữ liệu hiện có."]
    print(f"\n{len(valid_rows)}/{len(rows)} query có câu trả lời hợp lệ để đánh giá.")

    if not valid_rows:
        print("Không có câu trả lời hợp lệ để đánh giá.")
        return

    # 2. Chuẩn bị RAGAS dataset
    dataset = Dataset.from_list([
        {
            "question": r["question"],
            "answer":   r["answer"],
            "contexts": r["contexts"],
        }
        for r in valid_rows
    ])

    # 3. Cấu hình RAGAS dùng GPT-4o-mini
    print("\nCấu hình RAGAS với GPT-4o-mini...")
    judge_llm  = LangchainLLMWrapper(
        ChatOpenAI(model="gpt-4o-mini", api_key=settings.openai_api_key, temperature=0)
    )
    judge_emb  = LangchainEmbeddingsWrapper(
        OpenAIEmbeddings(model="text-embedding-3-small", api_key=settings.openai_api_key)
    )
    faithfulness_metric.llm        = judge_llm
    answer_relevancy_metric.llm        = judge_llm
    answer_relevancy_metric.embeddings = judge_emb

    # 4. Chạy RAGAS
    print("Chạy RAGAS evaluation (gọi GPT-4o-mini)...")
    t0 = time.time()
    ragas_result = evaluate(dataset, metrics=[faithfulness_metric, answer_relevancy_metric])
    eval_time = time.time() - t0

    scores = ragas_result.to_pandas()
    print(f"  Columns: {list(scores.columns)}")  # debug — xem tên column thực tế

    faith_col  = next((c for c in scores.columns if "faith" in c.lower()), None)
    relev_col  = next((c for c in scores.columns if "relev" in c.lower()), None)

    avg_faithfulness     = float(scores[faith_col].mean()) if faith_col else float("nan")
    avg_answer_relevancy = float(scores[relev_col].mean()) if relev_col else float("nan")
    avg_latency_ms        = sum(r["latency_ms"] for r in rows) / len(rows)
    answered_rate         = len(valid_rows) / len(rows)

    print(f"\n{'=' * 60}")
    print("KẾT QUẢ ĐÁNH GIÁ")
    print(f"{'=' * 60}")
    print(f"  Faithfulness      : {avg_faithfulness:.3f}  (1.0 = hoàn toàn bám context)")
    print(f"  Answer Relevancy  : {avg_answer_relevancy:.3f}  (1.0 = trả lời đúng câu hỏi)")
    print(f"  Answered rate     : {answered_rate:.0%}  ({len(valid_rows)}/{len(rows)} query có đáp án)")
    print(f"  Avg latency       : {avg_latency_ms:.0f}ms")
    print(f"  Eval time         : {eval_time:.1f}s")
    print()

    # Chi tiết từng câu
    print("Chi tiết từng câu:")
    for i, (row, (_, score_row)) in enumerate(zip(valid_rows, scores.iterrows()), 1):
        faith = score_row.get(faith_col, float("nan")) if faith_col else float("nan")
        relev = score_row.get(relev_col, float("nan")) if relev_col else float("nan")
        print(f"  [{i}] F={faith:.2f} R={relev:.2f} | {row['question'][:55]}")

    # 5. Log vào MLflow
    print("\nLog kết quả vào MLflow...")
    mlflow.set_experiment("rag_evaluation")

    with mlflow.start_run(run_name="ragas_eval"):
        # Params
        mlflow.log_param("embedding_model",  settings.embedding_model)
        mlflow.log_param("reranker_model",   settings.reranker_model)
        mlflow.log_param("llm_model",        settings.llm_model)
        mlflow.log_param("judge_model",      "gpt-4o-mini")
        mlflow.log_param("num_queries",      len(TEST_QUERIES))
        mlflow.log_param("num_evaluated",    len(valid_rows))

        # Metrics tổng hợp
        mlflow.log_metric("faithfulness",     avg_faithfulness)
        mlflow.log_metric("answer_relevancy", avg_answer_relevancy)
        mlflow.log_metric("answered_rate",    answered_rate)
        mlflow.log_metric("avg_latency_ms",   avg_latency_ms)

        # Metrics từng câu
        for i, (row, (_, score_row)) in enumerate(zip(valid_rows, scores.iterrows())):
            faith = score_row.get(faith_col, float("nan")) if faith_col else float("nan")
            relev = score_row.get(relev_col, float("nan")) if relev_col else float("nan")
            mlflow.log_metric(f"q{i+1}_faithfulness",     faith)
            mlflow.log_metric(f"q{i+1}_answer_relevancy", relev)
            mlflow.log_metric(f"q{i+1}_latency_ms",       row["latency_ms"])

        # Lưu full scores CSV
        scores_path = "/tmp/ragas_scores.csv"
        scores.to_csv(scores_path, index=False)
        mlflow.log_artifact(scores_path, artifact_path="scores")

        run_id = mlflow.active_run().info.run_id
        print(f"  Run ID: {run_id}")

    print("\nXem kết quả:")
    print("  mlflow ui  (mở http://localhost:5000)")


if __name__ == "__main__":
    asyncio.run(main())
