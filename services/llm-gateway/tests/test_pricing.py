import pytest

from llm_gateway.pricing import calc_cost
from llm_gateway.types import TokenUsage


def test_calc_cost_known_model():
    usage = TokenUsage(input_tokens=500_000, output_tokens=200_000)
    cost = calc_cost("claude-sonnet-5", usage)
    assert cost == pytest.approx(0.5 * 3.0 + 0.2 * 15.0)


def test_calc_cost_unknown_model_returns_zero():
    usage = TokenUsage(input_tokens=1000, output_tokens=1000)
    assert calc_cost("model-khong-ton-tai", usage) == 0.0


def test_calc_cost_zero_usage_is_zero():
    assert calc_cost("gpt-4o-mini", TokenUsage(0, 0)) == 0.0
