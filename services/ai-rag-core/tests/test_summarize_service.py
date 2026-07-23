import app.services.summarize_service as summarize_service  # type: ignore # noqa


def test_parse_period_uses_the_real_last_day_of_30_day_months():
    # Regression: hardcoding "-31" produced invalid dates (e.g. 2026-04-31) that
    # Neo4j's date() rejects, causing _fetch_articles() to silently return [].
    start, end = summarize_service._parse_period("2026-04")
    assert start == "2026-04-01"
    assert end == "2026-04-30"


def test_parse_period_uses_the_real_last_day_of_february():
    start, end = summarize_service._parse_period("2026-02")
    assert start == "2026-02-01"
    assert end == "2026-02-28"


def test_parse_period_uses_the_real_last_day_of_february_in_a_leap_year():
    start, end = summarize_service._parse_period("2024-02")
    assert start == "2024-02-01"
    assert end == "2024-02-29"


def test_parse_period_handles_31_day_months():
    start, end = summarize_service._parse_period("2026-07")
    assert start == "2026-07-01"
    assert end == "2026-07-31"


def test_parse_period_handles_a_quarter_ending_in_a_30_day_month():
    start, end = summarize_service._parse_period("2026-Q2")
    assert start == "2026-04-01"
    assert end == "2026-06-30"


def test_parse_period_handles_a_year():
    start, end = summarize_service._parse_period("2026")
    assert start == "2026-01-01"
    assert end == "2026-12-31"
