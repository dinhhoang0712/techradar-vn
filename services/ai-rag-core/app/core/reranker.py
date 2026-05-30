from functools import lru_cache
from pathlib import Path
from typing import Any

from app.config import get_settings


class OnnxReranker:
    def __init__(self, model_name: str, model_file: str) -> None:
        import onnxruntime as ort
        from huggingface_hub import snapshot_download
        from transformers import AutoTokenizer

        model_dir = snapshot_download(
            repo_id=model_name,
            allow_patterns=[
                model_file,
                "config.json",
                "sentencepiece.bpe.model",
                "special_tokens_map.json",
                "tokenizer.json",
                "tokenizer_config.json",
            ],
        )
        self.tokenizer = AutoTokenizer.from_pretrained(model_dir)
        self.session = ort.InferenceSession(
            str(Path(model_dir) / model_file),
            providers=["CPUExecutionProvider"],
        )
        self.input_names = {inp.name for inp in self.session.get_inputs()}

    def predict(self, pairs: list[tuple[str, str]]) -> list[float]:
        import numpy as np

        if not pairs:
            return []

        queries, passages = zip(*pairs)
        encoded = self.tokenizer(
            list(queries),
            list(passages),
            padding=True,
            truncation=True,
            max_length=512,
            return_tensors="np",
        )
        feed = {name: value for name, value in encoded.items() if name in self.input_names}
        logits = self.session.run(None, feed)[0]
        scores = np.asarray(logits)

        if scores.ndim == 2 and scores.shape[1] > 1:
            scores = scores[:, -1]
        else:
            scores = scores.reshape(-1)

        return [float(score) for score in scores]


@lru_cache(maxsize=1)
def get_reranker() -> Any:
    settings = get_settings()
    if settings.reranker_backend == "onnx":
        return OnnxReranker(settings.reranker_model, settings.reranker_onnx_file)

    from sentence_transformers import CrossEncoder

    return CrossEncoder(settings.reranker_model)


RERANK_SCORE_THRESHOLD = 0.40


def rerank(query: str, candidates: list[dict], top_k: int = 5) -> list[dict]:
    """
    Nhận query + danh sách candidate article từ vector search,
    trả về top_k article được rerank lại theo relevance score thực sự.

    Chỉ giữ lại các article có rerank_score >= RERANK_SCORE_THRESHOLD (0.40)
    để tránh đưa bài không liên quan vào prompt.

    candidates: list[dict] với keys: id, title, content, source, published_date, sentiment_score, score
    Trả về: list[dict] tương tự, thêm key `rerank_score`, sắp xếp giảm dần.
    """
    if not candidates:
        return []

    model = get_reranker()

    pairs = [
        (query, _build_passage(c))
        for c in candidates
    ]

    scores = model.predict(pairs)

    for candidate, score in zip(candidates, scores):
        candidate["rerank_score"] = float(score)

    reranked = sorted(candidates, key=lambda x: x["rerank_score"], reverse=True)

    filtered = [a for a in reranked if a["rerank_score"] >= RERANK_SCORE_THRESHOLD]
    return filtered[:top_k]


def _build_passage(candidate: dict, max_chars: int = 1000) -> str:
    """Ghép title + content thành passage để cho reranker chấm điểm."""
    title   = (candidate.get("title") or "").strip()
    content = (candidate.get("content") or "").strip()
    passage = f"{title} {content}".strip()
    return passage[:max_chars]
