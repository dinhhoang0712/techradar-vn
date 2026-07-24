from types import SimpleNamespace

from app.core.context_ranker import _group_sql_by_tech, rerank_context


def _passthrough_rerank(query, candidates, top_k):
    return candidates[:top_k]


def _fake_settings(rerank_top_k=5):
    return SimpleNamespace(rerank_top_k=rerank_top_k)


def test_rerank_context_reranks_each_source_type_independently(monkeypatch):
    monkeypatch.setattr("app.core.context_ranker.rerank", _passthrough_rerank)

    articles = [{"title": "A1", "content": "..."}]
    jobs = [{"title": "Dev", "company": "FPT"}]
    companies = [{"name": "FPT", "industry": "IT"}]
    sql_rows = [{"technology_name": "Java", "month": "2026-01", "job_count": 10}]

    result = rerank_context("query", articles, jobs, companies, sql_rows, settings=_fake_settings())

    assert result["articles"] == articles
    assert len(result["jobs"]) == 1
    assert result["jobs"][0]["title"] == "Dev"
    assert result["jobs"][0]["company"] == "FPT"
    assert len(result["companies"]) == 1
    assert result["companies"][0]["name"] == "FPT"
    assert result["analytics"] == sql_rows


def test_rerank_context_preserves_original_fields_alongside_synthetic_ones(monkeypatch):
    captured_candidates = {}

    def _capture(query, candidates, top_k):
        captured_candidates["jobs"] = candidates
        return candidates

    monkeypatch.setattr("app.core.context_ranker.rerank", _capture)

    jobs = [{"title": "Backend Dev", "company": "Tiki", "salary": "2000 USD"}]
    rerank_context("query", [], jobs, [], [], settings=_fake_settings())

    enriched = captured_candidates["jobs"][0]
    assert enriched["title"] == "Backend Dev"  # field gốc không bị ghi đè bởi setdefault
    assert enriched["company"] == "Tiki"
    assert enriched["salary"] == "2000 USD"
    assert "content" in enriched  # text tổng hợp cho reranker


def test_rerank_context_handles_all_empty_inputs():
    result = rerank_context("query", [], [], [], [], settings=_fake_settings())
    assert result == {"articles": [], "jobs": [], "companies": [], "analytics": []}


def test_group_sql_by_tech_groups_multiple_rows():
    rows = [
        {"technology_name": "Java", "month": "2026-01", "job_count": 5},
        {"technology_name": "Java", "month": "2026-02", "job_count": 8},
        {"technology_name": "Python", "month": "2026-01", "job_count": 3},
    ]

    grouped = _group_sql_by_tech(rows)

    names = {g["technology_name"] for g in grouped}
    assert names == {"Java", "Python"}
    java_group = next(g for g in grouped if g["technology_name"] == "Java")
    assert len(java_group["rows"]) == 2
