"""
Qdrant writer: consumes the embedding Kafka topics produced by the embedding service
(`article_vectors`, `job_vectors`) and upserts the 768-dim vectors into Qdrant collections
so they are available for semantic search.

Message shape (from embedding_service.py):
    {
      "message_type": "article_vector" | "job_vector",
      "id": "<md5 hex of source_url>",
      "source_url": "...",
      "source_platform": "...",
      "embedding": [768 floats],
      "metadata": { ... }
    }
"""
import json
import logging
import os
import uuid

from kafka import KafkaConsumer
from qdrant_client import QdrantClient
from qdrant_client.models import Distance, PointStruct, VectorParams

logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(message)s")
logger = logging.getLogger("qdrant_writer")

VECTOR_DIM = int(os.getenv("VECTOR_DIM", "768"))
ARTICLE_TOPIC = os.getenv("ARTICLE_VECTORS_TOPIC", "article_vectors")
JOB_TOPIC = os.getenv("JOB_VECTORS_TOPIC", "job_vectors")
ARTICLE_COLLECTION = os.getenv("QDRANT_ARTICLE_COLLECTION", "articles")
JOB_COLLECTION = os.getenv("QDRANT_JOB_COLLECTION", "jobs")
BOOTSTRAP = os.getenv("KAFKA_BOOTSTRAP_SERVERS", "localhost:9092").split(",")
GROUP_ID = os.getenv("QDRANT_WRITER_GROUP", "qdrant-writer")
QDRANT_URL = os.getenv("QDRANT_URL", "http://localhost:6333")
QDRANT_API_KEY = os.getenv("QDRANT_API_KEY") or None


def ensure_collections(client: QdrantClient) -> None:
    for name in (ARTICLE_COLLECTION, JOB_COLLECTION):
        if not client.collection_exists(name):
            client.create_collection(
                collection_name=name,
                vectors_config=VectorParams(size=VECTOR_DIM, distance=Distance.COSINE),
            )
            logger.info("Created Qdrant collection '%s' (dim=%s, cosine)", name, VECTOR_DIM)


def to_point_id(raw_id) -> str:
    """The message id is an MD5 hex (128-bit) -> deterministic UUID (a valid Qdrant point id)."""
    try:
        return str(uuid.UUID(hex=str(raw_id)))
    except (ValueError, TypeError):
        return str(uuid.uuid5(uuid.NAMESPACE_URL, str(raw_id)))


def to_point(msg: dict):
    embedding = msg.get("embedding")
    if not isinstance(embedding, list) or len(embedding) != VECTOR_DIM:
        logger.warning("Skipping message %s: missing/invalid embedding", msg.get("id"))
        return None
    payload = {
        "source_url": msg.get("source_url"),
        "source_platform": msg.get("source_platform"),
    }
    payload.update(msg.get("metadata") or {})
    return PointStruct(id=to_point_id(msg.get("id")), vector=embedding, payload=payload)


def collection_for(topic: str) -> str:
    return ARTICLE_COLLECTION if topic == ARTICLE_TOPIC else JOB_COLLECTION


def main() -> None:
    client = QdrantClient(url=QDRANT_URL, api_key=QDRANT_API_KEY)
    ensure_collections(client)

    consumer = KafkaConsumer(
        ARTICLE_TOPIC,
        JOB_TOPIC,
        bootstrap_servers=BOOTSTRAP,
        auto_offset_reset="earliest",
        enable_auto_commit=True,
        group_id=GROUP_ID,
        value_deserializer=lambda m: json.loads(m.decode("utf-8")),
    )
    logger.info("Qdrant writer listening on %s, %s -> %s", ARTICLE_TOPIC, JOB_TOPIC, QDRANT_URL)

    for record in consumer:
        try:
            point = to_point(record.value)
            if point is None:
                continue
            client.upsert(collection_name=collection_for(record.topic), points=[point])
            logger.info("Upserted %s into '%s'", point.id, collection_for(record.topic))
        except Exception:  # noqa: BLE001 - never let one bad message kill the consumer
            logger.exception("Failed to upsert message from %s", record.topic)


if __name__ == "__main__":
    main()
