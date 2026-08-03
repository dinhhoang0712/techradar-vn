from datetime import date

from app.core.flexible_date import parse_date


def test_parse_date_handles_iso_format():
    assert parse_date("2026-06-30") == date(2026, 6, 30)


def test_parse_date_handles_slash_ddmmyyyy_when_first_group_over_12():
    assert parse_date("29/06/2026") == date(2026, 6, 29)


def test_parse_date_handles_slash_mmddyyyy_when_second_group_over_12():
    assert parse_date("06/30/2026") == date(2026, 6, 30)


def test_parse_date_assumes_ddmmyyyy_for_ambiguous_slash_dates():
    # Both groups <= 12 — convention used by the crawled Vietnamese job sites (ITviec, TopCV).
    assert parse_date("05/06/2026") == date(2026, 6, 5)


def test_parse_date_returns_none_when_neither_slash_group_is_a_valid_day_or_month():
    assert parse_date("13/13/2026") is None


def test_parse_date_returns_none_for_none():
    assert parse_date(None) is None


def test_parse_date_returns_none_for_empty_string():
    assert parse_date("") is None


def test_parse_date_returns_none_for_unrecognized_format():
    assert parse_date("June 30, 2026") is None


def test_parse_date_returns_none_for_an_invalid_calendar_date():
    assert parse_date("2026-02-30") is None


def test_parse_date_ignores_a_time_suffix_on_an_iso_string():
    assert parse_date("2026-06-30T10:15:00Z") == date(2026, 6, 30)
