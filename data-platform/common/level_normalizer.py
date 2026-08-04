"""
Chuẩn hoá free-text "level" (job posting) về 1 trong 6 mức kinh nghiệm cố định.

Duplicate có chủ đích với bản Java `LevelNormalizer`
(apps/backend/.../features/kafka/domain/LevelNormalizer.java) — khác với tech alias
(dp_tech_alias_map, danh sách mở cần admin sửa), đây là 1 tập bucket cố định nhỏ nên
không cần bảng DB riêng; 2 bản phải giữ cùng thứ tự/keyword khi sửa.
"""

from __future__ import annotations

# Thứ tự ưu tiên: cấp cao nhất khớp trước, để "Senior Team Lead" -> Lead, không lẫn Senior.
_LEVEL_KEYWORDS: list[tuple[str, tuple[str, ...]]] = [
    ("Lead", ("lead", "trưởng nhóm", "team lead", "quản lý", "manager", "trưởng phòng",
              "phó phòng", "giám đốc", "director", "head of")),
    ("Senior", ("senior", "chuyên gia", "cấp cao")),
    ("Middle", ("middle", "mid-level", "mid level")),
    ("Junior", ("junior", "nhân viên", "staff")),
    ("Fresher", ("fresher", "mới tốt nghiệp", "mới ra trường", "entry level", "graduate")),
    ("Intern", ("intern", "thực tập")),
]


def normalize_level(raw: str | None) -> str | None:
    """Trả về 1 trong Intern/Fresher/Junior/Middle/Senior/Lead, hoặc None nếu không khớp bucket nào."""
    if not raw:
        return None
    text = raw.strip().lower()
    if not text:
        return None
    for level, keywords in _LEVEL_KEYWORDS:
        if any(kw in text for kw in keywords):
            return level
    return None
