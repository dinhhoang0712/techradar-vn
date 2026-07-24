from __future__ import annotations

import gold.neo4j_enricher as neo4j_enricher

# ---------------------------------------------------------------------------
# Fake Postgres conn/cursor (giống pattern FakeConn/FakeCursor của test_tech_dedup.py)
# ---------------------------------------------------------------------------


class FakeCursor:
    def __init__(self, fetchall_result=None):
        self._fetchall_result = fetchall_result or []

    def __enter__(self):
        return self

    def __exit__(self, exc_type, exc, tb):
        return False

    def execute(self, query, params=None):
        pass

    def fetchall(self):
        return self._fetchall_result


class FakeConn:
    def __init__(self, fetchall_result=None):
        self._fetchall_result = fetchall_result

    def cursor(self):
        return FakeCursor(self._fetchall_result)

    def close(self):
        pass


def test_fetch_categories_returns_rows_from_dp_tech_category():
    conn = FakeConn(
        fetchall_result=[
            {"canonical_name": "Kubernetes", "category": "tool"},
            {"canonical_name": "Python", "category": "language"},
        ]
    )

    rows = neo4j_enricher._fetch_categories(conn)

    assert rows == [
        {"canonical_name": "Kubernetes", "category": "tool"},
        {"canonical_name": "Python", "category": "language"},
    ]


def test_fetch_categories_empty_when_no_rows():
    conn = FakeConn(fetchall_result=[])
    assert neo4j_enricher._fetch_categories(conn) == []


# ---------------------------------------------------------------------------
# _COMPANY_USES_TECH_FROM_JOB — regression guard cho phát hiện thật: USES trước đây CHỈ suy ra
# từ Article co-mention, nhưng chỉ 6/425 Company từng được 1 Article nhắc tên — 419 công ty còn
# lại chỉ tồn tại qua Job posting nên không bao giờ có cạnh USES nào dù đang dùng rất nhiều công
# nghệ. Tín hiệu Job phải dùng cùng union POSTED_BY|HIRES_FOR như COMPANY_INSIGHT_CONTEXT (Java)
# để không bỏ sót company linkage của Job cũ, và phải lọc rõ t:Technology (REQUIRES cũng nối tới
# Skill, không được lẫn Skill vào USES).
# ---------------------------------------------------------------------------


def test_company_uses_tech_from_job_uses_posted_by_union_and_filters_technology():
    cypher = neo4j_enricher._COMPANY_USES_TECH_FROM_JOB
    assert "[:POSTED_BY|HIRES_FOR]" in cypher
    assert "(t:Technology)" in cypher


def test_company_uses_tech_from_article_unchanged():
    # Tín hiệu Article co-mention gốc vẫn giữ nguyên — bổ sung tín hiệu Job, không thay thế.
    cypher = neo4j_enricher._COMPANY_USES_TECH_FROM_ARTICLE
    assert "(a:Article)-[:MENTIONS]->(c:Company)" in cypher
    assert "(a)-[:MENTIONS]->(t:Technology)" in cypher


# ---------------------------------------------------------------------------
# run() — xác nhận company_uses_tech là TỔNG cả 2 tín hiệu, không phải chỉ Article
# ---------------------------------------------------------------------------


class _FakeRecord:
    def __init__(self, cnt):
        self._cnt = cnt

    def single(self):
        return {"cnt": self._cnt}


class _FakeSession:
    def __init__(self, routes):
        self._routes = routes

    def __enter__(self):
        return self

    def __exit__(self, exc_type, exc, tb):
        return False

    def run(self, query, params=None):
        for substring, cnt in self._routes.items():
            if substring in query:
                return _FakeRecord(cnt)
        return _FakeRecord(0)


class _FakeDriver:
    def __init__(self, routes):
        self._routes = routes
        self.closed = False

    def session(self):
        return _FakeSession(self._routes)

    def close(self):
        self.closed = True


def test_run_sums_article_and_job_uses_signals(monkeypatch):
    driver = _FakeDriver(
        {
            "(a:Article)-[:MENTIONS]->(c:Company)": 46,
            "(c:Company)<-[:POSTED_BY|HIRES_FOR]-(j:Job)": 2979,
        }
    )
    monkeypatch.setattr(neo4j_enricher, "get_neo4j_driver", lambda settings: driver)
    monkeypatch.setattr(neo4j_enricher, "get_pg_conn", lambda settings: FakeConn(fetchall_result=[]))
    monkeypatch.setattr(neo4j_enricher, "log_pipeline_run", lambda *a, **k: None)

    results = neo4j_enricher.run(settings=object())

    assert results["company_uses_tech"] == 46 + 2979
    assert driver.closed is True
