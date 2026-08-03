"""
Đánh giá RAG pipeline. 2 chế độ chấm điểm (EVAL_METRICS_MODE):

  "local" (MẶC ĐỊNH) — Faithfulness/Answer Relevancy tính bằng word-overlap + cosine similarity
      trên embedding model local của service — KHÔNG gọi LLM nào, không tốn quota Groq/Gemini/
      OpenAI. Yếu hơn RAGAS thật (không hiểu ngữ nghĩa, chỉ đo trùng lặp từ vựng/embedding thô)
      — dùng khi lặp lại đánh giá nhiều lần hoặc quota LLM bị giới hạn.

  "ragas" — RAGAS thật (Faithfulness + Answer Relevancy + Context Precision/Recall), judge:
      Groq (llama-3.3-70b-versatile). Đây là phương pháp chuẩn, có thể trích dẫn trong luận án,
      nhưng tốn quota LLM đáng kể (mỗi câu trả lời bị tách thành nhiều "claim" rồi kiểm chứng
      từng cái). Dùng cho lần đánh giá "chính thức" cuối cùng.

Cả 2 chế độ đều cần generate() thật (LLM sinh câu trả lời) — không có cách nào bỏ bước này,
vì đây chính là tính năng đang được đánh giá.

Dùng cho ablation study (Adaptive Hybrid Graph RAG): mỗi lần chạy gắn 1 "variant" (đọc từ env
var EVAL_VARIANT) tương ứng với tổ hợp cờ đang bật trong .env
(strategy_selector_enabled / graph_expansion_enabled / unified_rerank_enabled), để so sánh
nhiều run trong cùng 1 MLflow experiment mà không cần checkout code qua lại.

Cách chạy (từ thư mục src/ai-rag-core/):
    EVAL_VARIANT=full python -m scripts.evaluate_rag                       # local, mặc định
    EVAL_VARIANT=full EVAL_METRICS_MODE=ragas python -m scripts.evaluate_rag  # RAGAS thật
"""

import asyncio
import os
import sys
import time
from pathlib import Path

sys.path.insert(0, str(Path(__file__).parent.parent))

import warnings

import mlflow
from datasets import Dataset

warnings.filterwarnings("ignore", category=DeprecationWarning)

from app.config import get_settings
from app.core.pipeline import answer as rag_answer

# ── Bộ câu hỏi test ──────────────────────────────────────────────────────────
# ground_truth: câu trả lời/tài liệu tham chiếu đúng, dùng bởi context_precision/
# context_recall (2 metric này CẦN ground_truth trong ragas==0.1.16 — không có chế độ
# reference-free ở bản này). Để trống ("") nghĩa là CHƯA được duyệt — script sẽ tự bỏ qua
# 2 metric đó thay vì tính ra số liệu sai lệch.
#
# TRẠNG THÁI: bản NHÁP round 1 — mọi ground_truth bên dưới được viết bằng cách tự tra trực
# tiếp Postgres (tech_analytics) và Neo4j (Job/Company/Technology, KHÔNG qua code retriever
# của chính pipeline đang được đánh giá, để tránh circular — xem giải thích trong hội thoại)
# vào ngày dữ liệu được tra (tháng gần nhất có dữ liệu thật: 2026-07). Vẫn cần người có domain
# knowledge duyệt lại trước khi dùng số liệu context_precision/recall cho luận án — đặc biệt
# các câu có nhiều công ty trùng tên (VD "FPT" khớp 6 pháp nhân khác nhau — xem
# _check_duplicate_case_names/entity-resolution gap đã ghi trong kg_health_audit) và các câu
# phân tích xu hướng (tech_analytics có vài tháng tương lai/job_count=0 nhưng growth_rate khác 0,
# có vẻ là dữ liệu placeholder — ground_truth bên dưới đã lọc bỏ, chỉ dùng tháng 2026-07 làm mốc
# "gần nhất thật"). Khác với category classification (Phase 2c) vốn là phân loại khách quan,
# đây là dữ liệu điểm-tại-một-thời-điểm — cần re-tra lại nếu dữ liệu hệ thống thay đổi nhiều.

TEST_QUERIES: list[dict] = [
    # --- Bộ 8 câu gốc ---
    {
        "question": "Lương kỹ sư phần mềm ở Việt Nam hiện tại ra sao?",
        "ground_truth": (
            "Mức lương kỹ sư phần mềm tại Việt Nam rất đa dạng, phần lớn tin tuyển dụng ghi "
            '"Thương lượng" hoặc mức phổ biến khoảng 10-30 triệu VND/tháng; một số vị trí '
            "senior/dự án nước ngoài trả theo USD, khoảng 800-2500 USD/tháng."
        ),
    },
    {
        "question": "Công việc Python developer lương bao nhiêu?",
        "ground_truth": (
            "Hệ thống hiện có 144 tin tuyển dụng yêu cầu Python. Mức lương dao động rộng: vị trí "
            "fresher/junior khoảng 10-20 triệu VND/tháng, vị trí senior/cloud engineer khoảng "
            '1000-2500 USD/tháng; nhiều tin không công khai mức lương cụ thể ("Thương lượng" hoặc để trống).'
        ),
    },
    {
        "question": "Tôi muốn tìm việc Data Engineer dùng Kafka và Spark",
        "ground_truth": (
            "Có 6 tin tuyển dụng Data Engineer yêu cầu đồng thời cả Kafka và Spark (trong tổng số "
            "15 tin Data Engineer, 10 tin yêu cầu ít nhất một trong hai công nghệ này), ví dụ: "
            '"Data Engineer", "CV Phát triển Tích hợp Dữ liệu (Junior Data Engineer)", '
            '"CVCC tích hợp dữ liệu (Senior Data Engineer)".'
        ),
    },
    {
        "question": "FPT tuyển kỹ sư phần mềm không?",
        "ground_truth": (
            "Có. Hệ thống ghi nhận 15 tin tuyển dụng từ các pháp nhân liên quan tới FPT (FPT Software, "
            "FPT Telecom, FPT IS, Synnex FPT...), gồm các vị trí như Senior AI Engineer, C++ Developer, "
            "Data Engineer, Project Manager, Senior Python Engineer."
        ),
    },
    {
        "question": "Xu hướng AI trong ngành IT Việt Nam năm 2025 là gì?",
        "ground_truth": (
            "Theo dữ liệu phân tích tháng gần nhất (07/2026), nhu cầu tuyển dụng liên quan AI tăng mạnh: "
            "Machine Learning 107 tin (+245% so với tháng trước, +1428% so với cùng kỳ năm ngoái), LLM 39 "
            "tin (+129% MoM, +3800% YoY), ChatGPT 19 tin (+111% MoM, +1800% YoY) — cho thấy xu hướng AI "
            "đang tăng trưởng rõ rệt."
        ),
    },
    {
        "question": "Shopee đang tuyển vị trí gì?",
        "ground_truth": (
            "Hệ thống hiện KHÔNG có dữ liệu tin tuyển dụng nào từ Shopee (không tồn tại Company node nào "
            'khớp tên "Shopee" trong Neo4j) — câu trả lời đúng phải nêu rõ không có thông tin, không được '
            "bịa ra vị trí tuyển dụng."
        ),
    },
    {
        "question": "React developer cần kỹ năng gì?",
        "ground_truth": (
            "Các công nghệ thường xuất hiện cùng React trong cùng tin tuyển dụng, theo tần suất: "
            "JavaScript (21 tin), Python (20), CI/CD (20), TypeScript (18), Next.js (17), HTML/CSS (17), "
            "Java (16), SQL (16)."
        ),
    },
    {
        "question": "Lương DevOps engineer ở Hà Nội bao nhiêu?",
        "ground_truth": (
            'Phần lớn tin DevOps tại Hà Nội ghi "Thương lượng" hoặc không công khai mức lương; một ví dụ '
            'cụ thể có mức lương rõ: "Nhân Viên Middle Azure Devops" 30-35 triệu VND/tháng.'
        ),
    },
    # --- Bổ sung: phủ các nhánh mới (strategy selector / expansion / bug đã fix) ---
    # Không entity cụ thể → kỳ vọng use_graph=False, chỉ vector chạy.
    {
        "question": "Ngành công nghệ thông tin có triển vọng trong 5 năm tới không?",
        "ground_truth": (
            "Dữ liệu phân tích tháng gần nhất cho thấy tín hiệu tích cực: mức tăng trưởng nhu cầu tuyển "
            "dụng trung bình trên 427 công nghệ được theo dõi là +160%, dẫn đầu về số lượng tin tuyển dụng "
            "là AI (224 tin), Python (144), SQL (132), AWS (109) — cho thấy ngành CNTT đang trong giai đoạn "
            "tăng nhu cầu nhân lực."
        ),
    },
    # 1 tech entity, câu hỏi hẹp → kỳ vọng graph_expansion_depth=1.
    {
        "question": "Kotlin dùng để làm gì?",
        "ground_truth": (
            'Kotlin được phân loại là ngôn ngữ lập trình (category "language"), thường liên quan tới phát '
            "triển Android/di động — các công nghệ liên quan gần nhất trong đồ thị gồm Android, Java, "
            "Spring, Spring Boot, iOS, Swift, Flutter. Có 16 tin tuyển dụng yêu cầu Kotlin trong hệ thống."
        ),
    },
    # Nhiều tech + từ khoá so sánh → kỳ vọng graph_expansion_depth=2 (MULTIHOP_INTENT_PATTERNS).
    {
        "question": "So sánh Java và Python, nên học cái nào trước?",
        "ground_truth": (
            'Cả Java và Python đều được phân loại là ngôn ngữ lập trình (category "language"). Theo số '
            "liệu tuyển dụng hiện tại trong hệ thống, Python có 144 tin yêu cầu, Java có 92 tin — nhu cầu "
            "tuyển dụng Python hiện cao hơn Java trong dữ liệu hệ thống."
        ),
    },
    # Company-only — đúng dạng JOBS_BY_COMPANY từng luôn trả rỗng trước khi fix HIRES_FOR/POSTED_BY.
    {
        "question": "Tiki đang tuyển những vị trí nào?",
        "ground_truth": (
            'Hệ thống ghi nhận 1 tin tuyển dụng từ Tiki: "DevOps Engineer (Middle/Senior)", yêu cầu các '
            "công nghệ Cloud, Prometheus, Ansible."
        ),
    },
    # Từ khoá phân tích rõ ràng → kỳ vọng use_sql_analytics=True.
    {
        "question": "Xu hướng tuyển dụng ngành Data Science 6 tháng qua thế nào?",
        "ground_truth": (
            "Số tin tuyển dụng Data Science giữ ổn định quanh 5 tin/tháng từ cuối 2025 đến giữa 2026, sau "
            "đó tăng đột biến lên 16 tin vào tháng 07/2026 (+1500% so với tháng trước) — cho thấy xu hướng "
            "tăng mạnh trong tháng gần nhất."
        ),
    },
]


# ── Chạy pipeline, thu thập data ─────────────────────────────────────────────


async def run_pipeline_for_eval(queries: list[dict]) -> list[dict]:
    """Chạy từng query qua RAG pipeline, lấy answer + contexts + strategy explainability."""
    rows = []
    for i, q in enumerate(queries, 1):
        query = q["question"]
        print(f"  [{i}/{len(queries)}] {query[:60]}...")
        t0 = time.time()
        try:
            result = await rag_answer(query)
            elapsed = time.time() - t0

            # 1. Article contexts
            contexts = []
            for src in result.get("sources", []):
                title = src.get("title") or ""
                content = src.get("content") or ""
                if title or content:
                    contexts.append(f"{title}\n{content}".strip())

            # 2. Job context (structured data gửi vào prompt — nguồn chính cho câu hỏi tuyển dụng)
            job_ctx = result.get("job_context", "")
            if job_ctx and job_ctx != "(Không có dữ liệu tuyển dụng liên quan.)":
                contexts.append(job_ctx)

            if not contexts:
                contexts = ["(không có context)"]

            subgraph = result.get("subgraph") or {}
            strategy = result.get("strategy") or {}

            rows.append(
                {
                    "question": query,
                    "ground_truth": q.get("ground_truth", ""),
                    "answer": result.get("answer", ""),
                    "contexts": contexts,
                    "latency_ms": int(elapsed * 1000),
                    "strategy": strategy,
                    "num_graph_triples": len(subgraph.get("@graph", [])),
                }
            )
        except Exception as e:
            print(f"     ⚠ Lỗi: {e}")
            rows.append(
                {
                    "question": query,
                    "ground_truth": q.get("ground_truth", ""),
                    "answer": "",
                    "contexts": ["(lỗi pipeline)"],
                    "latency_ms": 0,
                    "strategy": {},
                    "num_graph_triples": 0,
                }
            )
    return rows


def _strategy_aggregates(rows: list[dict]) -> dict:
    """Explainability metrics — RAGAS không đánh giá được 'selector có hành xử hợp lý không',
    nên log riêng dựa trên field `strategy` mà pipeline.answer() đã trả về (Phase 1)."""
    n = len(rows) or 1
    strategies = [r["strategy"] for r in rows if r["strategy"]]
    return {
        "pct_queries_using_graph": sum(1 for s in strategies if s.get("use_graph")) / n,
        "pct_queries_using_expansion": sum(1 for s in strategies if (s.get("graph_expansion_depth") or 0) > 0) / n,
        "pct_queries_using_sql_analytics": sum(1 for s in strategies if s.get("use_sql_analytics")) / n,
        "avg_num_graph_triples": sum(r["num_graph_triples"] for r in rows) / n,
    }


def _log_common_mlflow_params(settings, variant: str, judge_model: str, num_evaluated: int) -> None:
    mlflow.log_param("variant", variant)
    mlflow.log_param("strategy_selector_enabled", settings.strategy_selector_enabled)
    mlflow.log_param("graph_expansion_enabled", settings.graph_expansion_enabled)
    mlflow.log_param("unified_rerank_enabled", settings.unified_rerank_enabled)
    mlflow.log_param("embedding_model", settings.embedding_model)
    mlflow.log_param("reranker_model", settings.reranker_model)
    mlflow.log_param("llm_model", settings.llm_model)
    mlflow.log_param("judge_model", judge_model)
    mlflow.log_param("num_queries", len(TEST_QUERIES))
    mlflow.log_param("num_evaluated", num_evaluated)


def _log_scores_artifact_best_effort(scores_path: str) -> None:
    try:
        mlflow.log_artifact(scores_path, artifact_path="scores")
    except Exception as e:
        # Không để lỗi ghi artifact làm mất toàn bộ metric đã log ở trên (đã xảy ra thật:
        # experiment "rag_evaluation" có artifact_location cũ trỏ tới path macOS không tồn
        # tại trên máy này — không phải lỗi code, không nên fatal cả lần chạy vì việc này).
        print(f"  ⚠ Không ghi được artifact CSV vào MLflow ({e}) — metric vẫn đã log OK.")


# ── Chế độ "local" — không gọi LLM nào để chấm điểm ──────────────────────────


def _cosine_similarity(a: list[float], b: list[float]) -> float:
    dot = sum(x * y for x, y in zip(a, b))
    norm_a = sum(x * x for x in a) ** 0.5
    norm_b = sum(y * y for y in b) ** 0.5
    return dot / (norm_a * norm_b) if norm_a and norm_b else 0.0


def _word_overlap_faithfulness(answer: str, contexts: list[str]) -> float:
    """Proxy thô cho faithfulness — tỷ lệ từ trong câu trả lời cũng xuất hiện trong context.
    Khác RAGAS thật (dùng LLM tách câu trả lời thành từng "claim" rồi kiểm chứng ngữ nghĩa
    từng claim với context), cách này chỉ đếm trùng lặp từ vựng thô — không hiểu được diễn
    đạt lại bằng từ khác (paraphrase) hay suy luận. Dùng khi cần né quota LLM, KHÔNG thay thế
    RAGAS cho số liệu chính thức của luận án.
    """
    answer_tokens = set(answer.lower().split())
    context_tokens = set(" ".join(contexts).lower().split())
    if not answer_tokens:
        return 0.0
    return len(answer_tokens & context_tokens) / len(answer_tokens)


def _compute_local_metrics(rows: list[dict]) -> list[dict]:
    """Faithfulness/answer_relevancy KHÔNG dùng LLM — dùng lại chính embedding model local đã
    có sẵn trong service (app.core.embedder, không tốn API) cho relevancy, và word-overlap
    thuần cho faithfulness."""
    from app.core.embedder import embed_query

    enriched = []
    for r in rows:
        if r["answer"]:
            faithfulness = _word_overlap_faithfulness(r["answer"], r["contexts"])
            relevancy = _cosine_similarity(embed_query(r["question"]), embed_query(r["answer"]))
        else:
            faithfulness = 0.0
            relevancy = 0.0
        enriched.append({**r, "local_faithfulness": faithfulness, "local_answer_relevancy": relevancy})
    return enriched


async def _run_local_eval(rows: list[dict], valid_rows: list[dict], variant: str, settings) -> None:
    print(
        "\n⚠ Chế độ LOCAL — faithfulness/answer_relevancy là proxy KHÔNG dùng LLM (word-overlap "
        "+ cosine similarity embedding local), YẾU HƠN RAGAS thật. Dùng số liệu này để lặp lại "
        "đánh giá nhanh, không dùng làm kết quả cuối cùng của luận án."
    )

    enriched = _compute_local_metrics(valid_rows)
    n = len(enriched) or 1
    avg_faithfulness = sum(r["local_faithfulness"] for r in enriched) / n
    avg_answer_relevancy = sum(r["local_answer_relevancy"] for r in enriched) / n
    avg_latency_ms = sum(r["latency_ms"] for r in rows) / len(rows)
    answered_rate = len(valid_rows) / len(rows)
    strategy_aggregates = _strategy_aggregates(rows)

    print(f"\n{'=' * 60}")
    print(f"KẾT QUẢ ĐÁNH GIÁ (LOCAL, không LLM) — variant={variant}")
    print(f"{'=' * 60}")
    print(f"  Faithfulness (proxy)      : {avg_faithfulness:.3f}")
    print(f"  Answer Relevancy (proxy)  : {avg_answer_relevancy:.3f}")
    print(f"  Answered rate             : {answered_rate:.0%}  ({len(valid_rows)}/{len(rows)} query có đáp án)")
    print(f"  Avg latency               : {avg_latency_ms:.0f}ms")
    print("\n  Strategy explainability:")
    for k, v in strategy_aggregates.items():
        print(f"    {k}: {v:.2f}")

    print("\nChi tiết từng câu:")
    for i, r in enumerate(enriched, 1):
        print(f"  [{i}] F={r['local_faithfulness']:.2f} R={r['local_answer_relevancy']:.2f} | {r['question'][:55]}")

    print("\nLog kết quả vào MLflow...")
    mlflow.set_experiment("rag_evaluation")
    with mlflow.start_run(run_name=f"local_eval_{variant}"):
        _log_common_mlflow_params(
            settings, variant, judge_model="none (local proxy, no LLM)", num_evaluated=len(valid_rows)
        )
        mlflow.log_param("has_ground_truth", False)
        mlflow.log_param("metrics_mode", "local")

        mlflow.log_metric("local_faithfulness", avg_faithfulness)
        mlflow.log_metric("local_answer_relevancy", avg_answer_relevancy)
        mlflow.log_metric("answered_rate", answered_rate)
        mlflow.log_metric("avg_latency_ms", avg_latency_ms)
        for k, v in strategy_aggregates.items():
            mlflow.log_metric(k, v)

        for i, r in enumerate(enriched, 1):
            mlflow.log_metric(f"q{i}_local_faithfulness", r["local_faithfulness"])
            mlflow.log_metric(f"q{i}_local_answer_relevancy", r["local_answer_relevancy"])
            mlflow.log_metric(f"q{i}_latency_ms", r["latency_ms"])

        import pandas as pd

        scores_path = "/tmp/local_eval_scores.csv"
        pd.DataFrame(enriched).to_csv(scores_path, index=False)
        _log_scores_artifact_best_effort(scores_path)

        print(f"  Run ID: {mlflow.active_run().info.run_id}")

    print("\nXem kết quả:")
    print("  mlflow ui  (mở http://localhost:5000)")


# ── Chế độ "ragas" — RAGAS thật, judge Groq (tốn quota LLM) ──────────────────


async def _run_ragas_eval(rows: list[dict], valid_rows: list[dict], variant: str, settings) -> None:
    from langchain_community.embeddings import HuggingFaceEmbeddings
    from langchain_groq import ChatGroq
    from ragas import evaluate
    from ragas.embeddings import LangchainEmbeddingsWrapper
    from ragas.llms import LangchainLLMWrapper
    from ragas.metrics._answer_relevance import answer_relevancy as answer_relevancy_metric
    from ragas.metrics._context_precision import context_precision as context_precision_metric
    from ragas.metrics._context_recall import context_recall as context_recall_metric
    from ragas.metrics._faithfulness import faithfulness as faithfulness_metric

    if not settings.groq_api_key:
        print("Lỗi: GROQ_API_KEY chưa được đặt trong .env")
        return

    # 2. Chuẩn bị RAGAS dataset — context_precision/recall chỉ tính được khi có ground_truth
    has_ground_truth = all(r.get("ground_truth") for r in valid_rows)
    if not has_ground_truth:
        print(
            "\n⚠ Một số/toàn bộ câu hỏi chưa có ground_truth (xem TODO ở TEST_QUERIES) — "
            "bỏ qua context_precision/context_recall, chỉ tính faithfulness/answer_relevancy."
        )

    dataset_rows = [
        {
            "question": r["question"],
            "answer": r["answer"],
            "contexts": r["contexts"],
            **({"ground_truth": r["ground_truth"]} if has_ground_truth else {}),
        }
        for r in valid_rows
    ]
    dataset = Dataset.from_list(dataset_rows)

    # 3. Cấu hình RAGAS dùng Groq (judge) + sentence-transformers local (embeddings — Groq
    # không có embeddings API, nên answer_relevancy dùng lại chính embedding model của service).
    print("\nCấu hình RAGAS với Groq...")
    judge_llm = LangchainLLMWrapper(
        ChatGroq(model="llama-3.3-70b-versatile", api_key=settings.groq_api_key, temperature=0)
    )
    judge_emb = LangchainEmbeddingsWrapper(HuggingFaceEmbeddings(model_name=settings.embedding_model))
    faithfulness_metric.llm = judge_llm
    answer_relevancy_metric.llm = judge_llm
    answer_relevancy_metric.embeddings = judge_emb

    metrics = [faithfulness_metric, answer_relevancy_metric]
    if has_ground_truth:
        context_precision_metric.llm = judge_llm
        context_recall_metric.llm = judge_llm
        metrics.extend([context_precision_metric, context_recall_metric])

    # 4. Chạy RAGAS
    print("Chạy RAGAS evaluation (gọi Groq)...")
    t0 = time.time()
    ragas_result = evaluate(dataset, metrics=metrics)
    eval_time = time.time() - t0

    scores = ragas_result.to_pandas()
    print(f"  Columns: {list(scores.columns)}")  # debug — xem tên column thực tế

    faith_col = next((c for c in scores.columns if "faith" in c.lower()), None)
    relev_col = next((c for c in scores.columns if "relev" in c.lower() and "context" not in c.lower()), None)
    ctx_precision_col = next((c for c in scores.columns if "context_precision" in c.lower()), None)
    ctx_recall_col = next((c for c in scores.columns if "context_recall" in c.lower()), None)

    avg_faithfulness = float(scores[faith_col].mean()) if faith_col else float("nan")
    avg_answer_relevancy = float(scores[relev_col].mean()) if relev_col else float("nan")
    avg_context_precision = float(scores[ctx_precision_col].mean()) if ctx_precision_col else float("nan")
    avg_context_recall = float(scores[ctx_recall_col].mean()) if ctx_recall_col else float("nan")
    avg_latency_ms = sum(r["latency_ms"] for r in rows) / len(rows)
    answered_rate = len(valid_rows) / len(rows)
    strategy_aggregates = _strategy_aggregates(rows)

    print(f"\n{'=' * 60}")
    print(f"KẾT QUẢ ĐÁNH GIÁ (RAGAS) — variant={variant}")
    print(f"{'=' * 60}")
    print(f"  Faithfulness      : {avg_faithfulness:.3f}  (1.0 = hoàn toàn bám context)")
    print(f"  Answer Relevancy  : {avg_answer_relevancy:.3f}  (1.0 = trả lời đúng câu hỏi)")
    if ctx_precision_col:
        print(f"  Context Precision : {avg_context_precision:.3f}  (1.0 = context lấy về đều liên quan)")
        print(f"  Context Recall    : {avg_context_recall:.3f}  (1.0 = lấy đủ thông tin liên quan)")
    print(f"  Answered rate     : {answered_rate:.0%}  ({len(valid_rows)}/{len(rows)} query có đáp án)")
    print(f"  Avg latency       : {avg_latency_ms:.0f}ms")
    print(f"  Eval time         : {eval_time:.1f}s")
    print("\n  Strategy explainability:")
    for k, v in strategy_aggregates.items():
        print(f"    {k}: {v:.2f}")
    print()

    print("Chi tiết từng câu:")
    for i, (row, (_, score_row)) in enumerate(zip(valid_rows, scores.iterrows()), 1):
        faith = score_row.get(faith_col, float("nan")) if faith_col else float("nan")
        relev = score_row.get(relev_col, float("nan")) if relev_col else float("nan")
        print(f"  [{i}] F={faith:.2f} R={relev:.2f} | {row['question'][:55]}")

    # 5. Log vào MLflow — cùng experiment cho mọi variant để so sánh trong 1 view.
    print("\nLog kết quả vào MLflow...")
    mlflow.set_experiment("rag_evaluation")

    with mlflow.start_run(run_name=f"ragas_eval_{variant}"):
        _log_common_mlflow_params(
            settings, variant, judge_model="llama-3.3-70b-versatile (groq)", num_evaluated=len(valid_rows)
        )
        mlflow.log_param("has_ground_truth", has_ground_truth)
        mlflow.log_param("metrics_mode", "ragas")

        mlflow.log_metric("faithfulness", avg_faithfulness)
        mlflow.log_metric("answer_relevancy", avg_answer_relevancy)
        if ctx_precision_col:
            mlflow.log_metric("context_precision", avg_context_precision)
            mlflow.log_metric("context_recall", avg_context_recall)
        mlflow.log_metric("answered_rate", answered_rate)
        mlflow.log_metric("avg_latency_ms", avg_latency_ms)
        for k, v in strategy_aggregates.items():
            mlflow.log_metric(k, v)

        for i, (row, (_, score_row)) in enumerate(zip(valid_rows, scores.iterrows())):
            faith = score_row.get(faith_col, float("nan")) if faith_col else float("nan")
            relev = score_row.get(relev_col, float("nan")) if relev_col else float("nan")
            mlflow.log_metric(f"q{i + 1}_faithfulness", faith)
            mlflow.log_metric(f"q{i + 1}_answer_relevancy", relev)
            mlflow.log_metric(f"q{i + 1}_latency_ms", row["latency_ms"])

        scores_path = "/tmp/ragas_scores.csv"
        scores.to_csv(scores_path, index=False)
        _log_scores_artifact_best_effort(scores_path)

        print(f"  Run ID: {mlflow.active_run().info.run_id}")

    print("\nXem kết quả:")
    print("  mlflow ui  (mở http://localhost:5000)")


# ── Main ──────────────────────────────────────────────────────────────────────


async def main() -> None:
    settings = get_settings()
    variant = os.environ.get("EVAL_VARIANT", "unspecified")
    mode = os.environ.get("EVAL_METRICS_MODE", "local")

    print("=" * 60)
    mode_desc = "RAGAS thật (Groq judge, tốn quota)" if mode == "ragas" else "LOCAL proxy (không tốn LLM)"
    print(f"Đánh giá RAG pipeline — chế độ: {mode_desc} — variant={variant}")
    print("=" * 60)

    # 1. Chạy pipeline lấy dữ liệu — LUÔN cần generate() thật, không phụ thuộc mode chấm điểm.
    print(f"\nChạy {len(TEST_QUERIES)} queries qua RAG pipeline...")
    rows = await run_pipeline_for_eval(TEST_QUERIES)

    valid_rows = [
        r
        for r in rows
        if r["answer"] and r["answer"] != "Tôi không tìm thấy thông tin liên quan trong dữ liệu hiện có."
    ]
    print(f"\n{len(valid_rows)}/{len(rows)} query có câu trả lời hợp lệ để đánh giá.")

    if not valid_rows:
        print("Không có câu trả lời hợp lệ để đánh giá.")
        return

    if mode == "ragas":
        await _run_ragas_eval(rows, valid_rows, variant, settings)
    else:
        await _run_local_eval(rows, valid_rows, variant, settings)


if __name__ == "__main__":
    asyncio.run(main())
