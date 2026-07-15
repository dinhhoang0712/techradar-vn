// Dev-only backfill for demo/local Neo4j: the real crawler pipeline never
// populates Article.sentiment_score (no sentiment-analysis stage exists yet)
// and the TopCV crawler hardcodes Job.salary = "" (TopCV listings don't show
// salary). Both fields are read by ai-rag-core (retriever/reranker/forecast)
// and the Salary feature, so they're worth faking for a believable demo.
// Idempotent: only fills nodes that are still null/empty. Run with cypher-shell.

// --- Article.sentiment_score: uniform-ish spread skewed slightly positive ---
MATCH (a:Article)
WHERE a.sentiment_score IS NULL
SET a.sentiment_score = round((rand() * 1.4 - 0.5) * 100) / 100.0;

// --- Job.salary: matches SalaryParser.java's accepted formats ---
// ~25% "Thỏa thuận" (negotiable, intentionally unparseable — mirrors real postings),
// ~75% a "<min> - <max> triệu" range in a plausible 10-70 trieu VND band.
MATCH (j:Job)
WHERE j.salary IS NULL OR trim(j.salary) = ''
WITH j, rand() AS bucket,
     toInteger(10 + floor(rand() * 8) * 5) AS baseSalary,
     toInteger(5 + floor(rand() * 3) * 5) AS spread
SET j.salary = CASE
    WHEN bucket < 0.25 THEN 'Thỏa thuận'
    ELSE toString(baseSalary) + ' - ' + toString(baseSalary + spread) + ' triệu'
END;
