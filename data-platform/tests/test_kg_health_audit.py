from __future__ import annotations

import gold.kg_health_audit as kg_health_audit

# ---------------------------------------------------------------------------
# Fakes — Neo4j driver/session, route theo substring của query (giống pattern
# test_retriever_graph.py bên ai-rag-core)
# ---------------------------------------------------------------------------


class FakeResult:
    def __init__(self, rows):
        self._rows = rows

    def data(self):
        return self._rows

    def single(self):
        return self._rows[0] if self._rows else None


class FakeSession:
    def __init__(self, routes: dict[str, list[dict]]):
        self._routes = routes

    def __enter__(self):
        return self

    def __exit__(self, exc_type, exc, tb):
        return False

    def run(self, query, params=None):
        for substring, rows in self._routes.items():
            if substring in query:
                return FakeResult(rows)
        return FakeResult([])


class FakeDriver:
    def __init__(self, routes: dict[str, list[dict]]):
        self._routes = routes
        self.closed = False

    def session(self):
        return FakeSession(self._routes)

    def close(self):
        self.closed = True


# ---------------------------------------------------------------------------
# _check_unknown_relationship_types
# ---------------------------------------------------------------------------


def test_check_unknown_relationship_types_flags_dead_rel_type():
    driver = FakeDriver(
        {
            "MATCH ()-[r]->()": [
                {"rel_type": "POSTED_BY", "cnt": 100},
                {"rel_type": "HIRES_FOR", "cnt": 12},
                {"rel_type": "REQUIRES", "cnt": 200},
            ]
        }
    )

    unknown = kg_health_audit._check_unknown_relationship_types(driver)

    assert unknown == [{"rel_type": "HIRES_FOR", "cnt": 12}]


def test_check_unknown_relationship_types_empty_when_all_known():
    driver = FakeDriver({"MATCH ()-[r]->()": [{"rel_type": "POSTED_BY", "cnt": 5}]})
    assert kg_health_audit._check_unknown_relationship_types(driver) == []


# ---------------------------------------------------------------------------
# _check_orphan_nodes
# ---------------------------------------------------------------------------


def test_check_orphan_nodes_returns_rows():
    driver = FakeDriver({"WHERE (n:Technology OR n:Company)": [{"label": "Technology", "cnt": 3}]})
    assert kg_health_audit._check_orphan_nodes(driver) == [{"label": "Technology", "cnt": 3}]


# ---------------------------------------------------------------------------
# _check_tech_property_coverage
# ---------------------------------------------------------------------------


def test_check_tech_property_coverage_computes_percentages():
    driver = FakeDriver(
        {
            "MATCH (t:Technology)\nRETURN count(t)": [
                {"total": 200, "with_category": 50, "with_pagerank": 100, "with_nan_pagerank": 0}
            ]
        }
    )

    coverage = kg_health_audit._check_tech_property_coverage(driver)

    assert coverage == {
        "total": 200,
        "category_coverage_pct": 25.0,
        "pagerank_coverage_pct": 50.0,
        "usable_pagerank_pct": 50.0,
    }


def test_check_tech_property_coverage_distinguishes_nan_from_usable():
    """Regression guard cho phát hiện thật: count(t.pagerank_score) đếm cả NaN (không phải
    NULL) — usable_pagerank_pct phải trừ NaN ra, không được báo phủ 100% khi thực ra là NaN."""
    driver = FakeDriver(
        {
            "MATCH (t:Technology)\nRETURN count(t)": [
                {"total": 100, "with_category": 0, "with_pagerank": 100, "with_nan_pagerank": 30}
            ]
        }
    )

    coverage = kg_health_audit._check_tech_property_coverage(driver)

    assert coverage["pagerank_coverage_pct"] == 100.0
    assert coverage["usable_pagerank_pct"] == 70.0


def test_check_tech_property_coverage_zero_when_no_technology_nodes():
    driver = FakeDriver(
        {
            "MATCH (t:Technology)\nRETURN count(t)": [
                {"total": 0, "with_category": 0, "with_pagerank": 0, "with_nan_pagerank": 0}
            ]
        }
    )

    coverage = kg_health_audit._check_tech_property_coverage(driver)

    assert coverage == {
        "total": 0,
        "category_coverage_pct": 0.0,
        "pagerank_coverage_pct": 0.0,
        "usable_pagerank_pct": 0.0,
    }


# ---------------------------------------------------------------------------
# _check_duplicate_case_names
# ---------------------------------------------------------------------------


def test_check_duplicate_case_names_returns_groups():
    driver = FakeDriver(
        {"WHERE size(names) > 1": [{"normalized": "react", "names": ["React", "react"]}]}
    )
    assert kg_health_audit._check_duplicate_case_names(driver) == [
        {"normalized": "react", "names": ["React", "react"]}
    ]


# ---------------------------------------------------------------------------
# run() — orchestration + driver lifecycle
# ---------------------------------------------------------------------------


def test_run_closes_driver_and_returns_full_report(monkeypatch):
    driver = FakeDriver(
        {
            "MATCH ()-[r]->()": [{"rel_type": "HIRES_FOR", "cnt": 1}],
            "WHERE (n:Technology OR n:Company)": [],
            "MATCH (t:Technology)\nRETURN count(t)": [
                {"total": 10, "with_category": 10, "with_pagerank": 10, "with_nan_pagerank": 0}
            ],
            "WHERE size(names) > 1": [],
        }
    )
    monkeypatch.setattr(kg_health_audit, "get_neo4j_driver", lambda settings: driver)

    report = kg_health_audit.run(settings=object())

    assert report["unknown_relationship_types"] == [{"rel_type": "HIRES_FOR", "cnt": 1}]
    assert report["orphan_nodes"] == []
    assert report["tech_property_coverage"]["category_coverage_pct"] == 100.0
    assert report["duplicate_case_names"] == []
    assert driver.closed is True


# ---------------------------------------------------------------------------
# _check_garbage_jobs
# ---------------------------------------------------------------------------


def test_check_garbage_jobs_returns_rows():
    driver = FakeDriver(
        {
            "coalesce(j.description, '')": [
                {"id": "abc123", "title": "Sorry, you have been blocked"},
                {"id": "def456", "title": "www.topcv.vn"},
            ]
        }
    )
    assert kg_health_audit._check_garbage_jobs(driver) == [
        {"id": "abc123", "title": "Sorry, you have been blocked"},
        {"id": "def456", "title": "www.topcv.vn"},
    ]


# ---------------------------------------------------------------------------
# _company_core / _word_boundary_contains / _check_company_near_duplicates
# ---------------------------------------------------------------------------


def test_company_core_strips_legal_boilerplate():
    assert kg_health_audit._company_core("Công Ty TNHH Reeracoen Việt Nam") == "reeracoen việt nam"
    assert kg_health_audit._company_core("CÔNG TY CỔ PHẦN VINSMART FUTURE") == "vinsmart future"
    assert kg_health_audit._company_core("One Mount Group") == "one mount group"


def test_word_boundary_contains_avoids_coincidental_substrings():
    """Regression guard cho phát hiện thật: substring thô ('insmart' in 'vinsmart') bắt nhầm 2
    công ty không liên quan — chỉ khớp khi nằm trọn ở ranh giới từ."""
    assert kg_health_audit._word_boundary_contains("one mount", "one mount group") is True
    assert kg_health_audit._word_boundary_contains("insmart", "vinsmart future") is False
    assert kg_health_audit._word_boundary_contains("gon tech", "saigon technology") is False


def test_check_company_near_duplicates_finds_legal_entity_variants():
    driver = FakeDriver(
        {
            "MATCH (c:Company)": [
                {"id": "one-mount", "name": "One Mount"},
                {"id": "one-mount-group", "name": "One Mount Group"},
                {"id": "doc-lap-abc", "name": "Công Ty Cổ Phần Độc Lập ABC"},
            ]
        }
    )
    groups = kg_health_audit._check_company_near_duplicates(driver)
    assert groups == [{"normalized_core": "one mount", "names": ["One Mount", "One Mount Group"]}]


def test_check_company_near_duplicates_excludes_garbage_long_names():
    """Regression guard cho phát hiện thật: 1 vài Company node có `name` là cả trang crawl lỗi
    dán nhầm vào (>200 ký tự) — phải loại khỏi phân tích near-duplicate, không được lẫn vào."""
    driver = FakeDriver(
        {
            "MATCH (c:Company)": [
                {"id": "one-mount", "name": "One Mount"},
                {"id": "garbage", "name": "x" * 300},
            ]
        }
    )
    groups = kg_health_audit._check_company_near_duplicates(driver)
    assert groups == []


def test_check_company_near_duplicates_pairs_identical_names():
    """Regression guard: phát hiện khi port logic này sang Java (Neo4jCompanyDuplicateAdapter) —
    duyệt cặp bằng so sánh lexicographic (name_a >= name_b) bỏ sót trường hợp 2 Company TRÙNG HỆT
    tên (thoả >= nên bị continue), dù đây là tín hiệu trùng lặp rõ ràng hơn cả biến thể pháp
    nhân. Đã sửa sang duyệt theo index + track theo `id` (không chỉ `name`) — 2 node vật lý khác
    nhau (id khác) dù trùng tên vẫn phải hiện diện như 2 entry riêng, không bị `set` gộp mất."""
    driver = FakeDriver(
        {
            "MATCH (c:Company)": [
                {"id": "abc-1", "name": "Công Ty ABC Việt Nam"},
                {"id": "abc-2", "name": "Công Ty ABC Việt Nam"},
            ]
        }
    )
    groups = kg_health_audit._check_company_near_duplicates(driver)
    assert len(groups) == 1
    assert groups[0]["names"] == ["Công Ty ABC Việt Nam", "Công Ty ABC Việt Nam"]


def test_run_closes_driver_even_on_exception(monkeypatch):
    class ExplodingDriver(FakeDriver):
        def session(self):
            raise RuntimeError("boom")

    driver = ExplodingDriver({})
    monkeypatch.setattr(kg_health_audit, "get_neo4j_driver", lambda settings: driver)

    try:
        kg_health_audit.run(settings=object())
        raised = False
    except RuntimeError:
        raised = True

    assert raised is True
    assert driver.closed is True
