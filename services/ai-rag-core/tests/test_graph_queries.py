from app.db import graph_queries

# Regression guard: the live Kafka writer and Gold sync only write [:POSTED_BY] onto
# (Job)->(Company); [:HIRES_FOR] is dead legacy data from a removed batch importer. Every
# query that resolves a Job's hiring Company must match the union, never HIRES_FOR alone.
_JOB_COMPANY_QUERIES = [
    "JOBS_BY_TECH_AND_TITLE",
    "JOBS_BY_TECH",
    "JOBS_BY_TITLE",
    "JOBS_BY_TITLE_AND_COMPANY",
    "JOBS_BY_COMPANY",
    "JOBS_BY_LOCATION",
]


def test_job_company_queries_use_posted_by_union():
    for name in _JOB_COMPANY_QUERIES:
        cypher = getattr(graph_queries, name)
        assert "[:POSTED_BY|HIRES_FOR]" in cypher, f"{name} must match POSTED_BY|HIRES_FOR, not HIRES_FOR alone"
        assert "[:HIRES_FOR]->" not in cypher, f"{name} still has a bare HIRES_FOR-only match"


def test_company_insight_context_unchanged():
    # This query was already correct before the fix — guard against regressing it.
    assert "[:POSTED_BY|HIRES_FOR]-(j:Job)" in graph_queries.COMPANY_INSIGHT_CONTEXT


def test_mandatory_match_queries_no_longer_always_empty():
    # JOBS_BY_TITLE_AND_COMPANY, JOBS_BY_COMPANY, JOBS_BY_LOCATION use a mandatory (non-OPTIONAL)
    # match on the Job->Company edge. Before the fix, these always returned zero rows in
    # production since the live writer never produces HIRES_FOR. Guard that the mandatory
    # match clause itself now includes POSTED_BY.
    for name in ["JOBS_BY_TITLE_AND_COMPANY", "JOBS_BY_COMPANY", "JOBS_BY_LOCATION"]:
        cypher = getattr(graph_queries, name)
        assert "MATCH (j:Job)-[:POSTED_BY|HIRES_FOR]->(c:Company)" in cypher


# Regression guard: two independent writers create :Job nodes — Neo4jExtractionWriter.java
# (Kafka realtime path) sets j.title, neo4j_job_sync.py (data-platform batch path, the
# dominant source — confirmed live: 901/907 real Job nodes) sets j.name instead. Confirmed
# live against real Neo4j: before this fix, every job title in RAG responses was NULL for
# ~99% of real job data. Every query returning/filtering a job title must read both properties.
_JOB_TITLE_QUERIES = [
    "JOBS_BY_TECH_AND_TITLE",
    "JOBS_BY_TECH",
    "JOBS_BY_TITLE",
    "JOBS_BY_TITLE_AND_COMPANY",
    "JOBS_BY_COMPANY",
    "JOBS_BY_LOCATION",
]


def test_job_title_queries_coalesce_title_and_name():
    for name in _JOB_TITLE_QUERIES:
        cypher = getattr(graph_queries, name)
        assert "coalesce(j.title, j.name) AS title" in cypher, f"{name} must return coalesce(j.title, j.name)"
        assert "j.title       AS title" not in cypher, f"{name} still returns bare j.title"


def test_job_title_keyword_filters_coalesce_title_and_name():
    # The 3 queries that filter by title keyword (not just return it) must match against
    # both properties too, or real (name-only) jobs never match the keyword search at all.
    for name in ["JOBS_BY_TECH_AND_TITLE", "JOBS_BY_TITLE", "JOBS_BY_TITLE_AND_COMPANY"]:
        cypher = getattr(graph_queries, name)
        assert "toLower(coalesce(j.title, j.name)) CONTAINS kw" in cypher, (
            f"{name} must filter on coalesce(j.title, j.name)"
        )
