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
WHERE toLower(j.title) CONTAINS kw
  AND (t:Technology OR t:Skill) AND toLower(t.name) IN $names
OPTIONAL MATCH (j)-[:HIRES_FOR]->(c:Company)
WITH DISTINCT j, c, collect(DISTINCT t.name)[..5] AS techs
RETURN
    j.title       AS title,
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
OPTIONAL MATCH (j)-[:HIRES_FOR]->(c:Company)
WITH DISTINCT j, t, c
RETURN
    j.title       AS title,
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
WHERE toLower(j.title) CONTAINS kw
OPTIONAL MATCH (j)-[:HIRES_FOR]->(c:Company)
OPTIONAL MATCH (j)-[:REQUIRES]->(t:Technology)
WITH DISTINCT j, c, collect(DISTINCT t.name)[..3] AS techs
RETURN
    j.title       AS title,
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
MATCH (j:Job)-[:HIRES_FOR]->(c:Company)
WHERE toLower(c.name) CONTAINS cname
OPTIONAL MATCH (j)-[:REQUIRES]->(t:Technology)
WITH DISTINCT j, c, collect(DISTINCT t.name)[..3] AS techs
RETURN
    j.title       AS title,
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
MATCH (j:Job)-[:HIRES_FOR]->(c:Company)
WHERE toLower(c.location) CONTAINS loc
OPTIONAL MATCH (j)-[:REQUIRES]->(t:Technology)
WITH DISTINCT j, c, collect(DISTINCT t.name)[..3] AS techs
RETURN
    j.title       AS title,
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
# TECH — related technologies via RELATED_TO (bidirectional)
# ---------------------------------------------------------------------------
TECH_RELATED = """
MATCH (t:Technology)-[:RELATED_TO]-(t2:Technology)
WHERE toLower(t.name) IN $names
RETURN DISTINCT t.name AS from_tech, t2.name AS related_tech
LIMIT 20
"""
