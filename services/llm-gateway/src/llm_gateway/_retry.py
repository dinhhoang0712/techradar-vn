"""Phát hiện lỗi nào từ SDK provider thì nên retry — dùng chung cho mọi adapter.

Kiểm tra status_code trước (chuẩn hơn), fallback sang match chuỗi lỗi (giữ tương thích
với cách generator.py/llm_labeler.py hiện tại đang làm khi status_code không có sẵn).
"""

from __future__ import annotations

_RETRYABLE_STATUS_CODES = {429, 500, 502, 503, 529}
_RETRYABLE_SUBSTRINGS = ("rate limit", "rate_limit", "overloaded", "service unavailable", "503", "429")


def is_retryable_error(exc: Exception) -> bool:
    status_code = getattr(exc, "status_code", None)
    if status_code in _RETRYABLE_STATUS_CODES:
        return True
    message = str(exc).lower()
    return any(s in message for s in _RETRYABLE_SUBSTRINGS)
