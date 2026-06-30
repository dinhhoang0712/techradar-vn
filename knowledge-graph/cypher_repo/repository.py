"""
Centralized Cypher query repository for Knowledge Graph build operations.

All queries used by database_connection.py and import_multi_source.py
live here as named string constants. Do NOT inline Cypher elsewhere in
the knowledge-graph package — add it here and import the constant.

Naming convention:
  SCHEMA_*     — constraints / indexes (DDL)
  MERGE_*      — node upserts
  REL_*        — relationship creation
  STAT_*       — read-only analytics / statistics
"""

# ---------------------------------------------------------------------------
# SCHEMA — constraints & indexes (idempotent, run once per DB)
# ---------------------------------------------------------------------------

SCHEMA_CONSTRAINTS: list[str] = [
    "CREATE CONSTRAINT IF NOT EXISTS FOR (a:Article)    REQUIRE a.title IS UNIQUE",
    "CREATE CONSTRAINT IF NOT EXISTS FOR (t:Technology) REQUIRE t.name  IS UNIQUE",
    "CREATE CONSTRAINT IF NOT EXISTS FOR (c:Company)    REQUIRE c.name  IS UNIQUE",
    "CREATE CONSTRAINT IF NOT EXISTS FOR (j:Job)        REQUIRE j.title IS UNIQUE",
    "CREATE CONSTRAINT IF NOT EXISTS FOR (s:Skill)      REQUIRE s.name  IS UNIQUE",
    "CREATE CONSTRAINT IF NOT EXISTS FOR (p:Person)     REQUIRE p.name  IS UNIQUE",
]

SCHEMA_INDEXES: list[str] = [
    "CREATE INDEX article_published_date    IF NOT EXISTS FOR (a:Article)    ON (a.published_date)",
    "CREATE INDEX article_source            IF NOT EXISTS FOR (a:Article)    ON (a.source)",
    "CREATE INDEX technology_category       IF NOT EXISTS FOR (t:Technology) ON (t.category)",
    "CREATE INDEX technology_subcategory    IF NOT EXISTS FOR (t:Technology) ON (t.subcategory)",
    "CREATE INDEX skill_category            IF NOT EXISTS FOR (s:Skill)      ON (s.category)",
    "CREATE INDEX company_location          IF NOT EXISTS FOR (c:Company)    ON (c.location)",
]

# 768-dim cosine vector index used by ai-rag-core RAG retrieval
SCHEMA_VECTOR_INDEX = (
    "CREATE VECTOR INDEX article_embedding_index IF NOT EXISTS "
    "FOR (a:Article) ON (a.embedding) "
    "OPTIONS {indexConfig: {`vector.dimensions`: 768, "
    "`vector.similarity_function`: 'cosine'}}"
)

# ---------------------------------------------------------------------------
# MERGE — node upserts
# ---------------------------------------------------------------------------

MERGE_TECHNOLOGY = """
MERGE (t:Technology {name: $name})
ON CREATE SET
    t.category    = $category,
    t.subcategory = $subcategory,
    t.description = $description,
    t.trend_score = $trend_score
ON MATCH SET
    t.category    = CASE WHEN t.category = 'Other' OR t.category IS NULL THEN $category ELSE t.category END,
    t.subcategory = CASE WHEN t.subcategory = 'General' OR t.subcategory IS NULL THEN $subcategory ELSE t.subcategory END
"""

MERGE_COMPANY = """
MERGE (c:Company {name: $name})
ON CREATE SET
    c.field    = $field,
    c.size     = $size,
    c.location = $location,
    c.rating   = $rating
ON MATCH SET
    c.location = CASE
        WHEN c.location = 'Unknown' OR c.location = '' THEN $location
        ELSE c.location
    END
"""

MERGE_JOB = """
MERGE (j:Job {title: $title})
ON CREATE SET
    j.description = $description,
    j.requirement = $requirement,
    j.benefit     = $benefit,
    j.salary      = $salary,
    j.due_date    = $due_date,
    j.source_url  = $source_url
"""

MERGE_SKILL = """
MERGE (s:Skill {name: $name})
ON CREATE SET
    s.category    = $category,
    s.demand_score = $demand_score
"""

MERGE_PERSON = """
MERGE (p:Person {name: $name})
ON CREATE SET
    p.role = $role
"""

MERGE_ARTICLE = """
MERGE (a:Article {title: $title})
ON CREATE SET
    a.content         = $content,
    a.source          = $source,
    a.published_date  = $published_date,
    a.sentiment_score = $sentiment_score
ON MATCH SET
    a.content = CASE
        WHEN a.content = '' OR a.content IS NULL THEN $content
        ELSE a.content
    END
"""

# ---------------------------------------------------------------------------
# REL — relationship creation (batch via UNWIND)
# ---------------------------------------------------------------------------

# Article → Technology / Company
REL_ARTICLE_MENTIONS_BATCH = """
UNWIND $pairs AS pair
MATCH (a:Article {title: pair.article_title})
MATCH (n {name: pair.node_name})
WHERE n:Technology OR n:Company
MERGE (a)-[:MENTIONS]->(n)
"""

# Job → Company
REL_JOB_HIRES_FOR_BATCH = """
UNWIND $pairs AS pair
MATCH (j:Job     {title: pair.job_title})
MATCH (c:Company {name:  pair.company_name})
MERGE (j)-[:HIRES_FOR]->(c)
"""

# Job → Technology
REL_JOB_REQUIRES_TECH_BATCH = """
UNWIND $pairs AS pair
MATCH (j:Job        {title: pair.job_title})
MATCH (t:Technology {name:  pair.tech_name})
MERGE (j)-[:REQUIRES {is_mandatory: true, frequency: 1}]->(t)
"""

# Job → Skill
REL_JOB_REQUIRES_SKILL_BATCH = """
UNWIND $pairs AS pair
MATCH (j:Job   {title: pair.job_title})
MATCH (s:Skill {name:  pair.skill_name})
MERGE (j)-[:REQUIRES {is_mandatory: true, frequency: 1}]->(s)
"""

# Derived: Company -[:USES]-> Technology (co-mention heuristic, threshold ≥ 2)
REL_COMPANY_USES_TECHNOLOGY = """
MATCH (a:Article)-[:MENTIONS]->(c:Company)
MATCH (a)-[:MENTIONS]->(t:Technology)
WITH c, t, count(a) AS co_mentions
WHERE co_mentions >= 2
MERGE (c)-[r:USES {frequency: co_mentions}]->(t)
RETURN count(r) AS rel_count
"""

# Derived: Technology -[:RELATED_TO]-> Technology (article co-mention)
REL_TECHNOLOGY_RELATED_TO = """
MATCH (a:Article)-[:MENTIONS]->(t1:Technology)
MATCH (a)-[:MENTIONS]->(t2:Technology)
WHERE t1.name < t2.name
WITH t1, t2, count(a) AS co_mentions
WHERE co_mentions >= 1
MERGE (t1)-[r:RELATED_TO {frequency: co_mentions}]->(t2)
RETURN count(r) AS rel_count
"""

# Derived: Person -[:WORKS_AT]-> Company (co-mention ≥ 2 articles)
REL_PERSON_WORKS_AT = """
MATCH (a:Article)-[:MENTIONS]->(p:Person)
MATCH (a)-[:MENTIONS]->(c:Company)
WITH p, c, count(a) AS mention_count
WHERE mention_count >= 2
MERGE (p)-[r:WORKS_AT {confidence: mention_count}]->(c)
RETURN count(r) AS rel_count
"""

# Derived: Person -[:WROTE]-> Article
REL_PERSON_WROTE_ARTICLE = """
MATCH (a:Article)-[:MENTIONS]->(p:Person)
MERGE (p)-[:WROTE]->(a)
RETURN count(*) AS rel_count
"""

# ---------------------------------------------------------------------------
# ANALYTICS — compute scores (read phase)
# ---------------------------------------------------------------------------

# Count article mentions per Technology within a time window.
# Param: $cutoff_date (ISO string, e.g. 30 days ago)
ANALYTICS_TECH_MENTION_COUNTS = """
MATCH (t:Technology)<-[:MENTIONS]-(a:Article)
WHERE a.published_date >= $cutoff_date
WITH t.name AS tech, count(a) AS mention_count
ORDER BY mention_count DESC
RETURN tech, mention_count
"""

# Count Job postings requiring each Technology
ANALYTICS_TECH_DEMAND_COUNTS = """
MATCH (t:Technology)<-[:REQUIRES]-(j:Job)
WITH t.name AS tech, count(j) AS job_count
ORDER BY job_count DESC
RETURN tech, job_count
"""

# Count Job postings requiring each Skill
ANALYTICS_SKILL_DEMAND_COUNTS = """
MATCH (s:Skill)<-[:REQUIRES]-(j:Job)
WITH s.name AS skill, count(j) AS job_count
ORDER BY job_count DESC
RETURN skill, job_count
"""

# ---------------------------------------------------------------------------
# ANALYTICS — update scores (write phase)
# ---------------------------------------------------------------------------

# Batch-update trend_score on Technology nodes
ANALYTICS_UPDATE_TECH_TREND_SCORES = """
UNWIND $scores AS s
MATCH (t:Technology {name: s.name})
SET t.trend_score = s.score
"""

# Batch-update demand_score on Technology nodes
ANALYTICS_UPDATE_TECH_DEMAND_SCORES = """
UNWIND $scores AS s
MATCH (t:Technology {name: s.name})
SET t.demand_score = s.score
"""

# Batch-update demand_score on Skill nodes
ANALYTICS_UPDATE_SKILL_DEMAND_SCORES = """
UNWIND $scores AS item
MATCH (s:Skill {name: item.name})
SET s.demand_score = item.score
"""

# ---------------------------------------------------------------------------
# STAT — read-only queries
# ---------------------------------------------------------------------------

STAT_NODE_COUNT = "MATCH (n:{label}) RETURN count(n) AS count"

STAT_RELATIONSHIP_COUNT = "MATCH ()-[r]->() RETURN count(r) AS count"

STAT_RELATIONSHIP_TYPE_COUNT = "MATCH ()-[r:{rel_type}]->() RETURN count(r) AS count"
