"""
Batching helpers dùng chung cho các job ghi hàng loạt vào Neo4j qua
`UNWIND $rows` (writeback theo batch để tránh query quá lớn / timeout).

Lưu ý liên-service: `data-platform/gold/neo4j_article_sync.py` (một service
khác, ETL Article → Neo4j) có một hàm `_chunks()` xử lý logic y hệt hàm
`chunks()` dưới đây. `ml-clustering` và `data-platform` hiện là hai service
độc lập (khác dependency tree / deploy unit) nên chưa có chỗ hợp lý để đặt
một package dùng chung; nếu sau này phát sinh thêm job Neo4j batch-write
thứ ba, nên tách các helper batching này ra một shared lib (vd.
`libs/graph_writeback_utils`) để mọi service cùng import thay vì mỗi nơi
tự viết lại 3 dòng generator này.
"""

from __future__ import annotations

from collections.abc import Iterator, Sequence
from typing import TypeVar

T = TypeVar("T")


def chunks(items: Sequence[T], size: int) -> Iterator[Sequence[T]]:
    """Chia `items` thành các chunk kích thước tối đa `size`."""
    for i in range(0, len(items), size):
        yield items[i : i + size]
