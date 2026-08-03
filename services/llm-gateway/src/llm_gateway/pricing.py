"""Bảng giá USD / 1M token — dùng để quy đổi TokenUsage ra cost_usd (bước Cost Tracking).

Cập nhật giá ở đây khi provider đổi giá. Model không có trong bảng -> cost 0.0,
không chặn request (chỉ có nghĩa là chưa tính được tiền cho model đó).
"""

from __future__ import annotations

from llm_gateway.types import TokenUsage

# {model_id: {"input": $/1M token input, "output": $/1M token output}}
PRICING: dict[str, dict[str, float]] = {
    # --- OpenAI ---
    "gpt-4o-mini": {"input": 0.15, "output": 0.60},
    "gpt-4o": {"input": 2.50, "output": 10.00},
    "gpt-4.1-mini": {"input": 0.40, "output": 1.60},
    # --- Groq (Llama) ---
    "llama-3.3-70b-versatile": {"input": 0.59, "output": 0.79},
    "llama-3.1-8b-instant": {"input": 0.05, "output": 0.08},
    # --- Gemini ---
    "gemini-1.5-flash": {"input": 0.075, "output": 0.30},
    "gemini-1.5-pro": {"input": 1.25, "output": 5.00},
    "gemini-2.0-flash": {"input": 0.10, "output": 0.40},
    # --- Claude ---
    "claude-opus-5": {"input": 5.00, "output": 25.00},
    "claude-sonnet-5": {"input": 3.00, "output": 15.00},
    "claude-haiku-4-5": {"input": 1.00, "output": 5.00},
}


def calc_cost(model: str, usage: TokenUsage) -> float:
    """Trả về số tiền USD cho 1 lần gọi, dựa trên bảng PRICING ở trên."""
    price = PRICING.get(model)
    if price is None:
        return 0.0
    return (usage.input_tokens / 1_000_000) * price["input"] + (usage.output_tokens / 1_000_000) * price["output"]
