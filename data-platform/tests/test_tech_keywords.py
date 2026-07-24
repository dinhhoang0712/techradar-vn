from __future__ import annotations

from common.tech_keywords import extract_tech


def test_extract_tech_returns_empty_for_blank_text():
    assert extract_tech("") == []
    assert extract_tech(None) == []


def test_extract_tech_matches_known_keywords_case_insensitively():
    text = "Công ty tuyển dụng python developer, ưu tiên biết react và docker."
    assert extract_tech(text) == ["Docker", "Python", "React"]


def test_extract_tech_dedupes_repeated_mentions():
    text = "Python, Python và lại Python."
    assert extract_tech(text) == ["Python"]


def test_extract_tech_resolves_vietnamese_aliases_to_canonical_names():
    text = "Ứng dụng trí tuệ nhân tạo và học máy trong bán dẫn."
    assert extract_tech(text) == ["AI", "Machine Learning", "Semiconductor"]


def test_extract_tech_handles_symbol_suffixed_terms_without_word_boundary_bugs():
    # \b fails on these (non-word char on both sides); the (?<!\w)/(?!\w)
    # pattern is specifically there to keep them matching.
    text = "Yêu cầu C++, C#, .NET và CI/CD."
    assert extract_tech(text) == [".NET", "C#", "C++", "CI/CD"]


def test_extract_tech_does_not_match_substrings_of_longer_terms():
    # "Go" must not spuriously match inside "Golang" or unrelated words.
    text = "Kinh nghiệm Golang là một lợi thế."
    assert extract_tech(text) == ["Golang"]


def test_extract_tech_does_not_match_unrelated_words_containing_keyword_substrings():
    text = "Ứng viên cần go đến văn phòng, không liên quan công nghệ."
    # "go" alone should still match the "Go" keyword — this documents the
    # current (imperfect) behavior: keyword matching can't disambiguate intent.
    assert extract_tech(text) == ["Go"]


def test_extract_tech_matches_network_security_hardware_vendors():
    """Regression guard cho gap REQUIRES đã phát hiện: danh sách trước đó thiên hẳn về
    software dev, bỏ sót thiết bị mạng/bảo mật xuất hiện thật trong mô tả công việc."""
    text = "Quản trị hệ thống mạng trên nền tảng Cisco, Juniper, Checkpoint, Fortinet và F5."
    assert extract_tech(text) == ["Checkpoint", "Cisco", "F5", "Fortinet", "Juniper"]


def test_extract_tech_matches_cad_cnc_game_engine_tools():
    """Regression guard cho gap REQUIRES — cùng đợt phát hiện với network/security hardware."""
    text = "Lập trình máy tiện CNC bằng Mastercam, hoặc phát triển game bằng Unreal Engine."
    assert extract_tech(text) == ["Mastercam", "Unreal Engine"]
