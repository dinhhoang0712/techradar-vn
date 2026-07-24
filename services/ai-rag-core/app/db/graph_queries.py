"""
Centralized Cypher query repository for RAG retrieval operations.

All Cypher used by retriever_graph.py lives here as named string constants.
Keep queries READ-ONLY (no MERGE/CREATE/DELETE) — this module is consumed
by the ai-rag-core service which must never mutate the graph.

Naming convention:
  JOBS_BY_*    — Job node lookups
  COMPANIES_*  — Company node lookups
  TECH_*       — Technology node lookups
"""

# ---------------------------------------------------------------------------
# JOBS — highest priority: matches BOTH title keyword AND required tech/skill
# ---------------------------------------------------------------------------
JOBS_BY_TECH_AND_TITLE = """
UNWIND $keywords AS kw
MATCH (j:Job)-[:REQUIRES]->(t)
WHERE toLower(coalesce(j.title, j.name)) CONTAINS kw
  AND (t:Technology OR t:Skill) AND toLower(t.name) IN $names
OPTIONAL MATCH (j)-[:POSTED_BY|HIRES_FOR]->(c:Company)
WITH DISTINCT j, c, collect(DISTINCT t.name)[..5] AS techs
RETURN
    coalesce(j.title, j.name) AS title,
    j.salary      AS salary,
    j.description AS description,
    j.benefit     AS benefit,
    j.requirement AS requirement,
    techs         AS technology,
    c.name        AS company,
    c.location    AS location
LIMIT 20
"""

# ---------------------------------------------------------------------------
# JOBS — matches required tech/skill only
# ---------------------------------------------------------------------------
JOBS_BY_TECH = """
MATCH (j:Job)-[:REQUIRES]->(t)
WHERE (t:Technology OR t:Skill) AND toLower(t.name) IN $names
OPTIONAL MATCH (j)-[:POSTED_BY|HIRES_FOR]->(c:Company)
WITH DISTINCT j, t, c
RETURN
    coalesce(j.title, j.name) AS title,
    j.salary      AS salary,
    j.description AS description,
    j.benefit     AS benefit,
    j.requirement AS requirement,
    t.name        AS technology,
    c.name        AS company,
    c.location    AS location
LIMIT 20
"""

# ---------------------------------------------------------------------------
# JOBS — matches title keyword only
# ---------------------------------------------------------------------------
JOBS_BY_TITLE = """
UNWIND $keywords AS kw
MATCH (j:Job)
WHERE toLower(coalesce(j.title, j.name)) CONTAINS kw
OPTIONAL MATCH (j)-[:POSTED_BY|HIRES_FOR]->(c:Company)
OPTIONAL MATCH (j)-[:REQUIRES]->(t:Technology)
WITH DISTINCT j, c, collect(DISTINCT t.name)[..3] AS techs
RETURN
    coalesce(j.title, j.name) AS title,
    j.salary      AS salary,
    j.description AS description,
    j.benefit     AS benefit,
    j.requirement AS requirement,
    techs         AS technology,
    c.name        AS company,
    c.location    AS location
LIMIT 20
"""

# ---------------------------------------------------------------------------
# JOBS — matches title keyword AND hiring company name (mock interview grounding)
# ---------------------------------------------------------------------------
JOBS_BY_TITLE_AND_COMPANY = """
UNWIND $keywords AS kw
MATCH (j:Job)-[:POSTED_BY|HIRES_FOR]->(c:Company)
WHERE toLower(coalesce(j.title, j.name)) CONTAINS kw AND toLower(c.name) CONTAINS toLower($company)
OPTIONAL MATCH (j)-[:REQUIRES]->(t:Technology)
WITH DISTINCT j, c, collect(DISTINCT t.name)[..3] AS techs
RETURN
    coalesce(j.title, j.name) AS title,
    j.salary      AS salary,
    j.description AS description,
    j.benefit     AS benefit,
    j.requirement AS requirement,
    techs         AS technology,
    c.name        AS company,
    c.location    AS location
LIMIT 20
"""

# ---------------------------------------------------------------------------
# JOBS — matches by hiring company name (NER ORG)
# ---------------------------------------------------------------------------
JOBS_BY_COMPANY = """
UNWIND $company_names AS cname
MATCH (j:Job)-[:POSTED_BY|HIRES_FOR]->(c:Company)
WHERE toLower(c.name) CONTAINS cname
OPTIONAL MATCH (j)-[:REQUIRES]->(t:Technology)
WITH DISTINCT j, c, collect(DISTINCT t.name)[..3] AS techs
RETURN
    coalesce(j.title, j.name) AS title,
    j.salary      AS salary,
    j.description AS description,
    j.benefit     AS benefit,
    j.requirement AS requirement,
    techs         AS technology,
    c.name        AS company,
    c.location    AS location
LIMIT 15
"""

# ---------------------------------------------------------------------------
# JOBS — matches by company location (NER LOC)
# ---------------------------------------------------------------------------
JOBS_BY_LOCATION = """
UNWIND $locations AS loc
MATCH (j:Job)-[:POSTED_BY|HIRES_FOR]->(c:Company)
WHERE toLower(c.location) CONTAINS loc
OPTIONAL MATCH (j)-[:REQUIRES]->(t:Technology)
WITH DISTINCT j, c, collect(DISTINCT t.name)[..3] AS techs
RETURN
    coalesce(j.title, j.name) AS title,
    j.salary      AS salary,
    j.description AS description,
    j.benefit     AS benefit,
    j.requirement AS requirement,
    techs         AS technology,
    c.name        AS company,
    c.location    AS location
LIMIT 15
"""

# ---------------------------------------------------------------------------
# COMPANIES — using the queried technologies
# ---------------------------------------------------------------------------
COMPANIES_USING_TECH = """
MATCH (c:Company)-[:USES]->(t:Technology)
WHERE toLower(t.name) IN $names
RETURN DISTINCT
    c.name     AS name,
    c.industry AS industry,
    c.location AS location,
    c.size     AS size,
    c.rating   AS rating,
    t.name     AS technology
LIMIT 15
"""

# ---------------------------------------------------------------------------
# COMPANIES — single company context for the AI Company Insight feature.
# Mirrors apps/backend's Neo4jCompanyRepository query (tech stack inferred from live job
# postings) rather than the derived Company-[:USES]->Technology edge, for consistency with
# what the Company page already shows.
# ---------------------------------------------------------------------------
COMPANY_INSIGHT_CONTEXT = """
MATCH (c:Company)
WHERE toLower(c.name) = toLower($company_name)
OPTIONAL MATCH (c)<-[:POSTED_BY|HIRES_FOR]-(j:Job)-[:REQUIRES]->(t)
WHERE t:Technology OR t:Skill
WITH c, collect(DISTINCT t.name) AS tech_stack, count(DISTINCT j) AS job_count
RETURN c.name AS name, c.location AS location, c.industry AS industry,
       c.size AS size, tech_stack, job_count
LIMIT 1
"""

# ---------------------------------------------------------------------------
# TECH — related technologies via RELATED_TO (bidirectional)
# ---------------------------------------------------------------------------
TECH_RELATED = """
MATCH (t:Technology)-[:RELATED_TO]-(t2:Technology)
WHERE toLower(t.name) IN $names
RETURN DISTINCT t.name AS from_tech, t2.name AS related_tech
LIMIT 20
"""

# ---------------------------------------------------------------------------
# SUBGRAPH EXPANSION — multi-hop (1-2 hop), seeded from entities extracted from the query.
# Built as a FUNCTION, not a plain string constant like the queries above — depth must be
# string-interpolated before run_query(), since Neo4j does not allow parameterizing a
# variable-length path bound (`*1..N`). Caller is responsible for clamping `depth` to a safe
# range (see settings.graph_max_hops) before calling this — never pass raw user input here.
#
# Returns flattened (subject, predicate, object) rows, not `RETURN p` (whole Path) — the
# shared run_query() helper calls Neo4j's `.data()`, which serializes Path/Node values by
# dropping labels/element-id, breaking downstream dedup/typing. Flattened rows also arrive
# already triple-shaped for graph_serializer.py, and `RETURN DISTINCT` on the projected
# columns handles dedup without a Python-side id-tracking map.
#
# ORDER BY pagerank_score prioritizes graph-central technologies first when the result set is
# truncated by LIMIT — this is the graph-aware-ranking piece, reusing PageRank scores already
# computed and persisted by the Java backend's GDS rebuild (apps/backend's
# Neo4jGraphAnalyticsAdapter), not something this service computes itself. coalesce(..., 0)
# handles nodes never covered by a GDS rebuild (property absent).
# ---------------------------------------------------------------------------


def subgraph_expand_query(depth: int, limit: int = 100) -> str:
    # pagerank_score phải được project thành cột riêng trong RETURN DISTINCT rồi ORDER BY theo
    # cột đó — Cypher không cho truy cập lại `rel`/`startNode(rel)` sau RETURN DISTINCT (biến
    # gốc ra khỏi scope), chỉ những gì đã được RETURN mới dùng được trong ORDER BY.
    #
    # coalesce(x, 0) CHỈ thay NULL bằng 0 — không thay được NaN, và trên dữ liệu thật một số
    # node có pagerank_score = NaN (không phải NULL, xác nhận qua Neo4j sống — có thể do node
    # không có cạnh RELATED_TO nào trong graph projection GDS dùng để tính PageRank). Phải check
    # isNaN() riêng, không dựa vào coalesce một mình.
    return f"""
    MATCH (n) WHERE (n:Technology OR n:Skill OR n:Company) AND toLower(n.name) IN $names
    MATCH path = (n)-[*1..{depth}]-(m)
    UNWIND relationships(path) AS rel
    RETURN DISTINCT
        startNode(rel).name AS subject, labels(startNode(rel))[0] AS subject_type,
        type(rel) AS predicate,
        endNode(rel).name AS object, labels(endNode(rel))[0] AS object_type,
        length(path) AS hop,
        CASE
            WHEN startNode(rel).pagerank_score IS NULL OR isNaN(startNode(rel).pagerank_score) THEN 0
            ELSE startNode(rel).pagerank_score
        END AS pagerank_score
    ORDER BY pagerank_score DESC
    LIMIT {limit}
    """
