// Sample knowledge graph for local demos (run with cypher-shell).
// Exercises every node label + relationship type, including the derived ones
// (USES / RELATED_TO / WORKS_AT / WROTE). Embeddings are not seeded here.

// --- Constraints + indexes (idempotent) ---
CREATE CONSTRAINT IF NOT EXISTS FOR (a:Article)    REQUIRE a.title IS UNIQUE;
CREATE CONSTRAINT IF NOT EXISTS FOR (t:Technology) REQUIRE t.name  IS UNIQUE;
CREATE CONSTRAINT IF NOT EXISTS FOR (c:Company)    REQUIRE c.name  IS UNIQUE;
CREATE CONSTRAINT IF NOT EXISTS FOR (j:Job)        REQUIRE j.title IS UNIQUE;
CREATE CONSTRAINT IF NOT EXISTS FOR (s:Skill)      REQUIRE s.name  IS UNIQUE;
CREATE CONSTRAINT IF NOT EXISTS FOR (p:Person)     REQUIRE p.name  IS UNIQUE;
CREATE INDEX article_published_date IF NOT EXISTS FOR (a:Article) ON (a.published_date);
CREATE VECTOR INDEX article_embedding_index IF NOT EXISTS
  FOR (a:Article) ON (a.embedding)
  OPTIONS {indexConfig: {`vector.dimensions`: 768, `vector.similarity_function`: 'cosine'}};

// --- Nodes ---
MERGE (py:Technology  {name: 'Python'})     SET py.category = 'Backend';
MERGE (dj:Technology  {name: 'Django'})     SET dj.category = 'Backend';
MERGE (re:Technology  {name: 'React'})      SET re.category = 'Frontend';
MERGE (sk:Skill       {name: 'REST API'})   SET sk.category = 'Backend';
MERGE (co:Company     {name: 'Acme Corp'})  SET co.location = 'Ha Noi', co.size = 'SME';
MERGE (jb:Job         {title: 'Backend Engineer'}) SET jb.salary = '20-30 trieu';
MERGE (pe:Person      {name: 'Nguyen An'})  SET pe.role = 'Author';
MERGE (ar:Article     {title: 'Python tang truong manh 2026'})
  SET ar.published_date = '2026-05-10', ar.source = 'VNExpress', ar.sentiment_score = 0.4;

// --- Base relationships ---
MATCH (ar:Article {title: 'Python tang truong manh 2026'}),
      (py:Technology {name: 'Python'}), (dj:Technology {name: 'Django'}),
      (co:Company {name: 'Acme Corp'}), (pe:Person {name: 'Nguyen An'})
MERGE (ar)-[:MENTIONS]->(py)
MERGE (ar)-[:MENTIONS]->(dj)
MERGE (ar)-[:MENTIONS]->(co)
MERGE (ar)-[:MENTIONS]->(pe);

MATCH (jb:Job {title: 'Backend Engineer'}),
      (py:Technology {name: 'Python'}), (sk:Skill {name: 'REST API'}), (co:Company {name: 'Acme Corp'})
MERGE (jb)-[:REQUIRES]->(py)
MERGE (jb)-[:REQUIRES]->(sk)
MERGE (jb)-[:HIRES_FOR]->(co);

// --- Derived relationships (same Cypher the import pipeline runs) ---
MATCH (a:Article)-[:MENTIONS]->(t1:Technology)
MATCH (a)-[:MENTIONS]->(t2:Technology)
WHERE t1.name < t2.name
WITH t1, t2, count(a) AS cm WHERE cm >= 1
MERGE (t1)-[r:RELATED_TO {frequency: cm}]->(t2);

MATCH (a:Article)-[:MENTIONS]->(p:Person)
MERGE (p)-[:WROTE]->(a);
