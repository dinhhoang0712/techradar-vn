from functools import lru_cache
from typing import Any

from app.config import get_settings


@lru_cache(maxsize=1)
def get_embedder() -> Any:
    from sentence_transformers import SentenceTransformer

    settings = get_settings()
    return SentenceTransformer(settings.embedding_model)


def embed_query(text: str) -> list[float]:
    """Dùng khi embed câu hỏi của user."""
    model = get_embedder()
    vector = model.encode(f"query: {text}", normalize_embeddings=True)
    return vector.tolist()


def embed_passage(text: str) -> list[float]:
    """Dùng khi embed Article để index vào Neo4j (script offline)."""
    model = get_embedder()
    vector = model.encode(f"passage: {text}", normalize_embeddings=True)
    return vector.tolist()


def embed_batch(texts: list[str], is_query: bool = False) -> list[list[float]]:
    """Embed nhiều văn bản cùng lúc — dùng trong script embed_articles.py."""
    model = get_embedder()
    prefix = "query: " if is_query else "passage: "
    prefixed = [f"{prefix}{t}" for t in texts]
    vectors = model.encode(prefixed, normalize_embeddings=True, batch_size=32, show_progress_bar=True)
    return [v.tolist() for v in vectors]
