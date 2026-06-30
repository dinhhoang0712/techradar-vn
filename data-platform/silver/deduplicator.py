"""
Deduplication helpers.
URL dedup (exact): ON CONFLICT (source_url) DO NOTHING — handled by SQL.
Content dedup (near): MD5 of normalised title+content stored as content_hash.
  Nếu content_hash đã tồn tại trong DB → đánh dấu is_duplicate=True.
"""
import hashlib
import re


def _normalise(text: str) -> str:
    text = text.lower().strip()
    text = re.sub(r"\s+", " ", text)
    return text


def content_hash(title: str, content: str) -> str:
    combined = _normalise(f"{title or ''} {content or ''}")
    return hashlib.md5(combined.encode("utf-8")).hexdigest()


def check_content_duplicate(conn, hash_val: str, current_id: str) -> str | None:
    """
    Trả về id của bài đã tồn tại nếu content_hash trùng (và đó không phải chính nó).
    Trả về None nếu không duplicate.
    """
    with conn.cursor() as cur:
        cur.execute(
            """SELECT id FROM dp_processed_articles
               WHERE content_hash = %s AND id != %s AND is_duplicate = FALSE
               LIMIT 1""",
            (hash_val, current_id),
        )
        row = cur.fetchone()
    return row["id"] if row else None


def check_job_duplicate(conn, hash_val: str, current_id: str) -> bool:
    with conn.cursor() as cur:
        cur.execute(
            """SELECT 1 FROM dp_processed_jobs
               WHERE content_hash = %s AND id != %s
               LIMIT 1""",
            (hash_val, current_id),
        )
        return cur.fetchone() is not None
