-- Dev-only: backfill nhiều tháng lịch sử cho tech_analytics — bám đúng pattern/schema của
-- V904 (cùng công thức job_count/article_count/growth theo months_ago), nhưng mở rộng số
-- lượng công nghệ và khoảng thời gian, để Radar trend chart có đủ điểm dữ liệu vẽ đường
-- (V904 seed cũ đã không còn — bị ETL thật ghi đè tháng hiện tại qua nhiều lần rebuild).
--
-- Cố tình bỏ qua months_ago = 0 (tháng hiện tại) — chỉ backfill quá khứ (tháng 1-11 trước),
-- để không ghi đè số liệu thật do RadarAnalyticsEtlService/pg_etl.py tính từ Neo4j. Dùng
-- ON CONFLICT DO NOTHING nên cũng không đụng tới các dòng lịch sử thật đã có (SQL/Python/AI...
-- có vài tháng lẻ tẻ từ bài viết có ngày tháng thật) — chỉ lấp vào chỗ trống.
-- Applied only under the dev Flyway location (not in prod).

WITH tech_base(technology_name, base_jobs, monthly_growth) AS (
    VALUES
        -- Khớp gần đúng mức hiện tại đã quan sát được (tháng 7/2026 thật từ ETL)
        ('SQL',               26,  0.8),
        ('Python',            23,  1.5),
        ('Machine Learning',  18,  1.8),
        ('Docker',            15,  1.0),
        ('Java',              13, -0.3),
        -- Ngôn ngữ / frontend / backend khác
        ('JavaScript',        20,  0.5),
        ('TypeScript',        17,  1.6),
        ('React',             19,  1.0),
        ('Go',                10,  1.4),
        ('Kotlin',             7,  0.9),
        ('Node.js',           14,  0.6),
        ('PHP',                9, -0.6),
        ('Rust',               5,  0.7),
        ('Spring Boot',       12,  0.4),
        ('.NET',              11, -0.2),
        -- Database
        ('PostgreSQL',        13,  1.2),
        ('MongoDB',           10,  0.5),
        ('Redis',              8,  0.7),
        -- Cloud / DevOps
        ('AWS',               16,  1.1),
        ('Azure',             12,  0.5),
        ('Kubernetes',        14,  1.6),
        ('CI/CD',              9,  0.8),
        -- Data / AI
        ('AI',                21,  2.2),
        ('Data Science',      10,  1.0)
),
months(months_ago) AS (
    SELECT generate_series(1, 11)
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
