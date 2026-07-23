"""
Cache trong RAM cho dp_tech_alias_map (Postgres) — nguồn chuẩn hoá tên
Technology dùng CHUNG với TechAliasCache.java (Java Kafka realtime), để
"Go"/"Golang", "ML"/"Machine Learning"... không tách thành 2 :Technology
node khác nhau trong Neo4j.

Refresh định kỳ (không phải mỗi message) — tra cache là 1 lần đọc dict
trong RAM, không có round-trip Postgres nào trên luồng xử lý message.
"""

from __future__ import annotations

import time

from loguru import logger

_alias_by_normalized: dict[str, str] = {}
_last_refresh_at: float = 0.0
_REFRESH_INTERVAL_SECONDS = 300  # 5 phút, khớp với TechAliasCache.java


def _refresh(conn) -> None:
    global _alias_by_normalized, _last_refresh_at
    try:
        with conn.cursor() as cur:
            cur.execute("SELECT alias_normalized, canonical_name FROM dp_tech_alias_map")
            rows = cur.fetchall()
        _alias_by_normalized = {row["alias_normalized"]: row["canonical_name"] for row in rows}
        _last_refresh_at = time.monotonic()
        logger.info("TechAliasCache (Python) refreshed: {} alias entries", len(_alias_by_normalized))
    except Exception as exc:
        logger.warning(
            "TechAliasCache (Python) refresh failed, giữ cache cũ ({} entries): {}",
            len(_alias_by_normalized),
            exc,
        )


def refresh_if_stale(conn) -> None:
    """
    Refresh cache nếu đã quá _REFRESH_INTERVAL_SECONDS kể từ lần refresh trước.
    Gọi ở đầu mỗi vòng lặp poll Kafka — rẻ (chỉ so sánh thời gian), chỉ thực
    sự query Postgres khi đã hết hạn, không chặn xử lý message.
    """
    if time.monotonic() - _last_refresh_at >= _REFRESH_INTERVAL_SECONDS:
        _refresh(conn)


def resolve(raw_name: str) -> str:
    """Trả tên canonical nếu có alias khớp (casefold), ngược lại trả nguyên tên đã strip."""
    if not raw_name:
        return raw_name
    trimmed = raw_name.strip()
    return _alias_by_normalized.get(trimmed.lower(), trimmed)


def canonicalize_techs(techs: list[str]) -> list[str]:
    """Áp resolve() cho cả danh sách + dedup (giữ thứ tự xuất hiện đầu tiên)."""
    if not techs:
        return []
    seen: dict[str, None] = {}
    for t in techs:
        if t is None:
            continue
        canonical = resolve(t)
        if canonical:
            seen.setdefault(canonical, None)
    return list(seen.keys())
