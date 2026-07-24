from app.config import get_settings
from app.db.graph_queries import subgraph_expand_query
from app.db.neo4j_client import run_query


async def expand_subgraph(tech_names: list[str], company_names: list[str], depth: int) -> list[dict]:
    """
    Mở rộng đồ thị multi-hop (1-2 hop) từ tech/company đã trích được — dùng khi Strategy
    Selector quyết định graph_expansion_depth > 0.

    Seed từ tech_names/company_names đã chuẩn hoá (KHÔNG dùng node Job/Company cụ thể mà 6
    query single-hop trong graph_queries.py đã match — job title match kiểu CONTAINS, không
    neo tên chính xác nên không phù hợp làm seed `name IN $names`). Seed theo cách này cũng
    giữ việc gọi hàm này chạy song song với graph_search() ở pipeline.py — không phụ thuộc
    kết quả của nhau.

    Trả về [] nếu không tìm thấy triple nào — không cần fallback round-trip thêm, vì
    graph_search() trong cùng request đã có sẵn dữ liệu 1-hop (jobs/companies/related_tech).

    depth được clamp phòng thủ ngay tại đây (không chỉ tin caller đã clamp) — đây là nơi
    depth bị string-interpolate thẳng vào Cypher, không phải tham số bind được.
    """
    names = [n.lower() for n in [*tech_names, *company_names]]
    if not names:
        return []

    settings = get_settings()
    clamped_depth = max(1, min(depth, settings.graph_max_hops, 3))

    cypher = subgraph_expand_query(depth=clamped_depth, limit=settings.graph_expansion_limit)
    return await run_query(cypher, {"names": names})
