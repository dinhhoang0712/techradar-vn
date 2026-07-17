from __future__ import annotations

from datetime import date

from gold.pg_etl import _growth, _parse_ym, _prev_month, _year_ago


def test_parse_ym_returns_first_of_month():
    assert _parse_ym("2026-04") == date(2026, 4, 1)


def test_parse_ym_returns_none_for_invalid_input():
    assert _parse_ym("not-a-date") is None
    assert _parse_ym("") is None


def test_growth_returns_zero_when_previous_is_none_or_zero():
    assert _growth(50, None) == 0.0
    assert _growth(50, 0) == 0.0


def test_growth_computes_percentage_change():
    assert _growth(150, 100) == 50.0
    assert _growth(50, 100) == -50.0


def test_growth_rounds_to_two_decimals():
    assert _growth(1, 3) == round((1 - 3) / 3 * 100, 2)


def test_prev_month_within_same_year():
    assert _prev_month("2026-05") == "2026-04"


def test_prev_month_rolls_back_across_year_boundary():
    assert _prev_month("2026-01") == "2025-12"


def test_year_ago_keeps_month_and_decrements_year():
    assert _year_ago("2026-04") == "2025-04"
