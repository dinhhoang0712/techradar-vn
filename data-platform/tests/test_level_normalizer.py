from __future__ import annotations

from common.level_normalizer import normalize_level


def test_normalizes_each_bucket_english_keyword():
    assert normalize_level("Senior Backend Developer") == "Senior"
    assert normalize_level("Middle Java Developer") == "Middle"
    assert normalize_level("Junior QA Engineer") == "Junior"
    assert normalize_level("Fresher - Entry Level") == "Fresher"
    assert normalize_level("Summer Intern") == "Intern"
    assert normalize_level("Engineering Manager") == "Lead"


def test_normalizes_each_bucket_vietnamese_keyword():
    assert normalize_level("Nhân viên kinh doanh") == "Junior"
    assert normalize_level("Trưởng nhóm Backend") == "Lead"
    assert normalize_level("Mới tốt nghiệp") == "Fresher"
    assert normalize_level("Thực tập sinh IT") == "Intern"
    assert normalize_level("Chuyên gia dữ liệu") == "Senior"


def test_tie_break_prefers_most_senior_match():
    assert normalize_level("Senior Team Lead") == "Lead"


def test_returns_none_for_empty_or_none():
    assert normalize_level("") is None
    assert normalize_level(None) is None
    assert normalize_level("   ") is None


def test_returns_none_when_no_keyword_matches():
    assert normalize_level("Full Stack Developer") is None
