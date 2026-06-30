"""
Prometheus metrics cho ai-rag-core.
Endpoint /metrics được register trong main.py.
"""
from prometheus_client import Counter, Histogram

ai_requests_total = Counter(
    "ai_rag_requests_total",
    "Total requests to ai-rag-core by endpoint",
    ["endpoint", "status", "llm_provider"],
)

ai_latency_seconds = Histogram(
    "ai_rag_latency_seconds",
    "Request latency by stage",
    ["endpoint", "stage"],
    buckets=[0.05, 0.1, 0.3, 0.5, 1.0, 2.0, 5.0, 10.0, 30.0],
)

llm_tokens_total = Counter(
    "ai_rag_llm_tokens_total",
    "LLM token consumption",
    ["provider", "model", "token_type"],
)

retrieval_results = Histogram(
    "ai_rag_retrieval_results",
    "Number of results returned per retrieval source",
    ["source"],
    buckets=[0, 1, 2, 5, 10, 20, 50],
)
