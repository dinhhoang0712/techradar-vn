"""Mỗi adapter là 1 file riêng, theo khuôn LLMProvider trong base.py.

Import optional — service nào không cài extra của provider đó (ví dụ không cài
`llm-gateway[claude]`) thì import module tương ứng sẽ lỗi ImportError, nhưng
không ảnh hưởng tới các provider khác vẫn dùng được.
"""

from llm_gateway.providers.base import LLMProvider

__all__ = ["LLMProvider"]
