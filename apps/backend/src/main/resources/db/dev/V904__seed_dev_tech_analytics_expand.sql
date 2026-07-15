-- Dev-only: broaden tech_analytics beyond the 3 technologies seeded in V901 so
-- Radar/Compare (top10, trend charts) look populated. Ranking is derived per
-- month from job_count so it stays internally consistent across technologies.
-- Applied only under the dev Flyway location (not in prod).

WITH tech_base(technology_name, base_jobs, monthly_growth) AS (
    VALUES
        ('Python',     70,  2.5),
        ('JavaScript', 60,  1.0),
        ('TypeScript', 45,  3.0),
        ('React',      50,  1.5),
        ('Go',         30,  4.0),
        ('Java',       55, -0.5),
        ('Node.js',    40,  1.2),
        ('Kotlin',     18,  2.0)
),
months(months_ago) AS (
    SELECT generate_series(0, 5)
),
computed AS (
    SELECT
        t.technology_name,
        (date_trunc('month', now()) - (m.months_ago || ' months')::interval)::date AS month,
        GREATEST(round(t.base_jobs - m.months_ago * t.monthly_growth * 2)::int, 5) AS job_count,
        GREATEST(round((t.base_jobs - m.months_ago * t.monthly_growth * 2) * 0.35)::int, 2) AS article_count,
        round(t.monthly_growth::numeric, 1) AS growth_rate,
        round((t.monthly_growth * 4)::numeric, 1) AS yoy_growth,
        round(t.monthly_growth::numeric, 1) AS mom_growth
    FROM tech_base t
    CROSS JOIN months m
)
INSERT INTO tech_analytics (technology_name, month, job_count, article_count, growth_rate, yoy_growth, mom_growth, ranking)
SELECT
    technology_name, month, job_count, article_count, growth_rate, yoy_growth, mom_growth,
    RANK() OVER (PARTITION BY month ORDER BY job_count DESC) AS ranking
FROM computed
ON CONFLICT (technology_name, month) DO NOTHING;
