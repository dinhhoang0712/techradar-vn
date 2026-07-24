"""
Gold — Knowledge Graph Quality Audit (chạy on-demand, không nằm trong lịch nightly mặc định)

Quét Neo4j để phát hiện các lớp vấn đề chất lượng đồ thị — thiết kế trực tiếp từ 1 bug thật
đã tìm thấy trong dự án này: quan hệ (Job)-[:HIRES_FOR]->(Company) đã ngừng được ghi (writer cũ
đã bị xoá khỏi repo) nhưng vẫn tồn tại trong dữ liệu VÀ vẫn được nhiều Cypher query trong
ai-rag-core match nhầm, khiến job/company retrieval âm thầm mất dữ liệu trong thời gian dài mà
không ai phát hiện. Script này tự động hoá đúng loại phát hiện đó, cộng thêm vài chỉ số chất
lượng đồ thị khác.

Cách chạy (từ thư mục data-platform/):
    python3 -m gold.kg_health_audit
"""

from __future__ import annotations

import json
import re

from common.db import get_neo4j_driver
from common.logger import configure_logging
from config import Settings, get_settings
from loguru import logger

# Quan hệ mà 1 writer đang hoạt động (Kafka realtime hoặc Gold batch sync) thực sự ghi ra —
# xem docs/DATABASE.md + Neo4jExtractionWriter.java + gold/neo4j_*.py. Bất kỳ loại quan hệ nào
# tồn tại trong graph nhưng KHÔNG nằm trong tập này là dấu hiệu writer cũ đã bị gỡ (giống hệt
# HIRES_FOR) — cần rà soát thủ công, KHÔNG tự xoá (dữ liệu lịch sử có thể vẫn cần đọc, như
# COMPANY_INSIGHT_CONTEXT cố ý match cả POSTED_BY|HIRES_FOR để không bỏ sót).
_KNOWN_ACTIVE_REL_TYPES = {
    "MENTIONS",
    "REQUIRES",
    "POSTED_BY",
    "USES",
    "RELATED_TO",
    "BELONGS_TO",
    "NEAR_CLUSTER",
}

_REL_TYPE_COUNTS_QUERY = """
MATCH ()-[r]->()
RETURN type(r) AS rel_type, count(r) AS cnt
ORDER BY cnt DESC
"""

_ORPHAN_NODES_QUERY = """
MATCH (n)
WHERE (n:Technology OR n:Company) AND NOT (n)--()
RETURN labels(n)[0] AS label, count(n) AS cnt
"""

_TECH_PROPERTY_COVERAGE_QUERY = """
MATCH (t:Technology)
RETURN count(t) AS total,
       count(t.category) AS with_category,
       count(t.pagerank_score) AS with_pagerank,
       count(CASE WHEN t.pagerank_score IS NOT NULL AND isNaN(t.pagerank_score) THEN 1 END) AS with_nan_pagerank
"""

_DUPLICATE_CASE_NAMES_QUERY = """
MATCH (t:Technology)
WITH toLower(t.name) AS normalized, collect(t.name) AS names
WHERE size(names) > 1
RETURN normalized, names
"""

# Phát hiện trực tiếp từ 1 bug thật tìm thấy trong dự án: crawler TopCV bị chặn (anti-bot/WAF)
# và lưu nhầm trang lỗi/captcha thành :Job node thay vì bỏ qua — xác nhận sống: 48/907 Job node
# có title = "sorry, you have been blocked" hoặc domain trần "www.topcv.vn", company/salary/
# description đều rỗng. `coalesce(j.description, '') = ''` là điều kiện lọc an toàn: mọi tin
# tuyển dụng thật, kể cả tin ngắn nhất, luôn có company hoặc description — chỉ trang lỗi mới
# rỗng hoàn toàn cả 2. Root-cause fix nằm ở `silver/processor.py` (chặn từ gốc, không cho vào
# Postgres); check này chỉ để phát hiện phần đã lọt qua trước khi có fix, hoặc tái phát sau này.
_GARBAGE_JOB_QUERY = """
MATCH (j:Job)
WITH j, toLower(coalesce(j.title, j.name, '')) AS t
WHERE coalesce(j.description, '') = ''
  AND (t CONTAINS 'blocked' OR t CONTAINS 'captcha' OR t CONTAINS 'access denied'
       OR t CONTAINS 'cloudflare' OR t =~ '^(www\\.)?[a-z0-9-]+\\.(vn|com|net|org)$')
RETURN j.id AS id, coalesce(j.title, j.name) AS title
"""

_COMPANY_NODES_QUERY = "MATCH (c:Company) RETURN c.id AS id, c.name AS name"

# Heuristic phát hiện Company trùng cùng 1 tổ chức nhưng khác tên pháp lý (vd "FPT Software" vs
# "Công Ty Cổ Phần Viễn Thông FPT") — CHỈ để phát hiện/đưa vào diện review, KHÔNG tự động gộp
# (gộp sai Company sẽ làm sai job_count/company_size, rủi ro cao hơn hẳn gộp Technology).
#
# Cách làm: bóc bỏ boilerplate pháp lý (Công Ty/TNHH/Cổ Phần/Chi Nhánh/Tập Đoàn/Trách Nhiệm Hữu
# Hạn viết đầy đủ...) rồi so khớp phần lõi còn lại — khớp theo TỪ TRỌN VẸN (word-boundary), không
# phải substring thô. Thử nghiệm trên dữ liệu thật cho thấy substring thô gây nhiễu nặng: 1 từ
# tiếng Việt đơn âm tiết chung chung (vd "học", "đại", "tại") trùng ngẫu nhiên giữa hàng chục
# Company không liên quan (140 nhóm nhiễu); còn substring không theo word-boundary vẫn bắt nhầm
# các cặp trùng ký tự ngẫu nhiên (vd "Insmart" nằm lọt trong "VinSmart", "gon tech" nằm lọt trong
# "Saigon Technology") dù là 2 công ty hoàn toàn khác nhau. Khớp theo word-boundary loại bỏ cả 2
# loại nhiễu này, còn lại ~11 nhóm đều là ứng viên hợp lý trên dữ liệu thật.
#
# Đây vẫn là heuristic, KHÔNG hoàn hảo: có thể bỏ sót các case tên viết hoàn toàn khác cấu trúc
# (vd không phải mọi biến thể "FPT" đều lọt cùng 1 nhóm — chỉ những cặp có phần lõi trùng khớp
# trực tiếp). Nếu cần độ chính xác/độ phủ cao hơn, hướng đi tiếp theo là dùng LLM để so khớp cặp
# tên, giống hệt cách `tech_dedup.py` Giai đoạn B xử lý case Technology mơ hồ — chưa làm ở đây.
_COMPANY_BOILERPLATE_PHRASES = [
    r"công\s*ty\s*trách\s*nhiệm\s*hữu\s*hạn",
    r"trách\s*nhiệm\s*hữu\s*hạn",
    r"công\s*ty\s*cổ\s*phần",
    r"công\s*ty\s*tnhh",
    r"tổng\s*công\s*ty",
    r"một\s*thành\s*viên",
    r"\bmtv\b",
    r"\btnhh\b",
    r"\bjsc\b",
    r"chi\s*nhánh",
    r"văn\s*phòng\s*đại\s*diện",
    r"tập\s*đoàn",
    r"\bcông\s*ty\b",
    r"\bcổ\s*phần\b",
    r"\bco\.?,?\s*ltd\.?\b",
    r"\bltd\.?\b",
    r"\bcorporation\b",
    r"\bcorp\.?\b",
    r"\bpro\s*company\b",
]
_COMPANY_BOILERPLATE_RE = re.compile("|".join(_COMPANY_BOILERPLATE_PHRASES), re.IGNORECASE | re.UNICODE)
_COMPANY_CORE_MIN_LEN = 6
_COMPANY_NAME_MAX_LEN = 200  # loại tên bất thường dài (trang crawl lỗi dán nhầm vào field name)


def _check_unknown_relationship_types(driver) -> list[dict]:
    with driver.session() as session:
        rows = session.run(_REL_TYPE_COUNTS_QUERY).data()
    return [r for r in rows if r["rel_type"] not in _KNOWN_ACTIVE_REL_TYPES]


def _check_orphan_nodes(driver) -> list[dict]:
    with driver.session() as session:
        return session.run(_ORPHAN_NODES_QUERY).data()


def _check_tech_property_coverage(driver) -> dict:
    """
    with_pagerank (count(t.pagerank_score)) đếm mọi giá trị KHÁC NULL — kể cả NaN, vì NaN
    không phải NULL trong Cypher. Xác nhận qua Neo4j sống: một số node có pagerank_score=NaN
    thật (node không có cạnh RELATED_TO nào trong graph projection GDS dùng để tính PageRank),
    nên "có property" khác với "có giá trị dùng được" — báo cáo riêng usable_pagerank_pct để
    không đánh giá quá lạc quan tỷ lệ phủ.
    """
    with driver.session() as session:
        row = session.run(_TECH_PROPERTY_COVERAGE_QUERY).single()
    if not row or not row["total"]:
        return {
            "total": 0,
            "category_coverage_pct": 0.0,
            "pagerank_coverage_pct": 0.0,
            "usable_pagerank_pct": 0.0,
        }
    total = row["total"]
    usable_pagerank = row["with_pagerank"] - row["with_nan_pagerank"]
    return {
        "total": total,
        "category_coverage_pct": round(row["with_category"] / total * 100, 1),
        "pagerank_coverage_pct": round(row["with_pagerank"] / total * 100, 1),
        "usable_pagerank_pct": round(usable_pagerank / total * 100, 1),
    }


def _check_duplicate_case_names(driver) -> list[dict]:
    with driver.session() as session:
        return session.run(_DUPLICATE_CASE_NAMES_QUERY).data()


def _check_garbage_jobs(driver) -> list[dict]:
    with driver.session() as session:
        return session.run(_GARBAGE_JOB_QUERY).data()


def _company_core(name: str) -> str:
    n = _COMPANY_BOILERPLATE_RE.sub(" ", name or "")
    n = re.sub(r"[^\w\s]", " ", n, flags=re.UNICODE)
    n = re.sub(r"\s+", " ", n).strip().lower()
    return n


def _word_boundary_contains(shorter: str, longer: str) -> bool:
    return f" {shorter} " in f" {longer} "


def _check_company_near_duplicates(driver) -> list[dict]:
    with driver.session() as session:
        rows = session.run(_COMPANY_NODES_QUERY).data()

    # Track theo (id, name) chứ không chỉ name — nếu chỉ gom theo tên, 2 node vật lý KHÁC NHAU
    # nhưng trùng hệt display name sẽ bị 1 `set` gộp lại thành 1 chuỗi duy nhất, làm mất tín hiệu
    # "đây là 2 node riêng biệt". Khớp cách Neo4jCompanyDuplicateAdapter.java làm (Map keyed theo
    # id, không phải name).
    cores = [
        (row["id"], row["name"], _company_core(row["name"]))
        for row in rows
        if row["id"] and row["name"] and len(row["name"]) <= _COMPANY_NAME_MAX_LEN
    ]
    cores = [(id_, name, core) for id_, name, core in cores if len(core) >= _COMPANY_CORE_MIN_LEN]

    # Duyệt theo INDEX (i < j), không dùng "name_a >= name_b" để bỏ qua cặp đã xét — cách so sánh
    # lexicographic đó có lỗ hổng: 2 Company TRÙNG HỆT tên (name_a == name_b) thoả >=, nên bị
    # continue và không bao giờ được ghép cặp, dù trùng tên y hệt là tín hiệu trùng lặp còn rõ
    # ràng hơn cả biến thể pháp nhân. Phát hiện khi port logic này sang Java
    # (Neo4jCompanyDuplicateAdapter.java) — sửa lại đây cho nhất quán cả 2 bên.
    groups: dict[str, dict[str, str]] = {}
    for i in range(len(cores)):
        id_a, name_a, core_a = cores[i]
        for j in range(i + 1, len(cores)):
            id_b, name_b, core_b = cores[j]
            shorter, longer = (core_a, core_b) if len(core_a) <= len(core_b) else (core_b, core_a)
            if core_a == core_b or _word_boundary_contains(shorter, longer):
                group = groups.setdefault(shorter, {})
                group[id_a] = name_a
                group[id_b] = name_b

    return [
        {"normalized_core": core, "names": sorted(id_to_name.values())}
        for core, id_to_name in sorted(groups.items(), key=lambda kv: -len(kv[1]))
    ]


def run(settings: Settings) -> dict:
    logger.info("KG Health Audit: starting...")
    driver = get_neo4j_driver(settings)

    try:
        unknown_rel_types = _check_unknown_relationship_types(driver)
        orphan_nodes = _check_orphan_nodes(driver)
        property_coverage = _check_tech_property_coverage(driver)
        duplicate_names = _check_duplicate_case_names(driver)
        garbage_jobs = _check_garbage_jobs(driver)
        company_near_duplicates = _check_company_near_duplicates(driver)

        report = {
            "unknown_relationship_types": unknown_rel_types,
            "orphan_nodes": orphan_nodes,
            "tech_property_coverage": property_coverage,
            "duplicate_case_names": duplicate_names,
            "garbage_jobs": garbage_jobs,
            "company_near_duplicates": company_near_duplicates,
        }

        if unknown_rel_types:
            logger.warning(
                "KG Health Audit: {} loại quan hệ KHÔNG nằm trong danh sách writer đang hoạt "
                "động (có thể là dead relationship như HIRES_FOR): {}",
                len(unknown_rel_types),
                [r["rel_type"] for r in unknown_rel_types],
            )
        if orphan_nodes:
            logger.warning("KG Health Audit: node mồ côi (không có quan hệ nào): {}", orphan_nodes)
        logger.info(
            "KG Health Audit: Technology property coverage — category {}%, pagerank_score "
            "{}% (dùng được: {}%) ({} node)",
            property_coverage["category_coverage_pct"],
            property_coverage["pagerank_coverage_pct"],
            property_coverage["usable_pagerank_pct"],
            property_coverage["total"],
        )
        if duplicate_names:
            logger.warning(
                "KG Health Audit: {} nhóm tên Technology trùng chỉ khác hoa/thường, chưa gộp: {}",
                len(duplicate_names),
                duplicate_names,
            )
        if garbage_jobs:
            logger.warning(
                "KG Health Audit: {} Job node là dữ liệu rác từ crawl bị chặn/lỗi (không phải tin "
                "tuyển dụng thật): {}",
                len(garbage_jobs),
                [j["title"] for j in garbage_jobs][:5],
            )
        if company_near_duplicates:
            logger.warning(
                "KG Health Audit: {} nhóm Company nghi trùng cùng tổ chức khác tên pháp lý (heuristic, "
                "cần người duyệt trước khi gộp): {}",
                len(company_near_duplicates),
                [g["normalized_core"] for g in company_near_duplicates],
            )

        logger.info("KG Health Audit: done")
        return report

    finally:
        driver.close()


if __name__ == "__main__":
    configure_logging()
    result = run(get_settings())
    print(json.dumps(result, indent=2, ensure_ascii=False))
