-- Dev-only sample data so the app shows something without crawling/ETL.
-- Applied only under the dev Flyway location (not in prod).

-- A demo non-admin user (password: Demo@12345)
INSERT INTO users (id, email, password_hash, full_name, role, status, subscription_tier, created_at, updated_at)
VALUES (
    '00000000-0000-0000-0000-000000000002',
    'demo@techradar.vn',
    crypt('Demo@12345', gen_salt('bf', 10)),
    'Demo User', 'user', 'active', 'free', now(), now()
) ON CONFLICT (email) DO NOTHING;

INSERT INTO user_profile (user_id, job_role, technologies, location, bio)
VALUES (
    '00000000-0000-0000-0000-000000000002',
    'Backend Engineer', ARRAY['Java', 'Spring', 'PostgreSQL'], 'Ha Noi', 'Demo profile'
) ON CONFLICT (user_id) DO NOTHING;

-- Sample radar/compare time series (6 months for 3 technologies)
INSERT INTO tech_analytics (technology_name, month, job_count, article_count, growth_rate, yoy_growth, mom_growth, ranking)
VALUES
    ('Python',     date_trunc('month', now())::date - INTERVAL '5 months', 40, 12, 5.0, 20.0, 5.0, 2),
    ('Python',     date_trunc('month', now())::date - INTERVAL '1 month',  55, 18, 8.0, 30.0, 8.0, 1),
    ('Python',     date_trunc('month', now())::date,                       60, 20, 9.0, 33.0, 9.0, 1),
    ('JavaScript', date_trunc('month', now())::date - INTERVAL '1 month',  50, 22, 4.0, 15.0, 4.0, 2),
    ('JavaScript', date_trunc('month', now())::date,                       52, 24, 4.5, 16.0, 4.5, 2),
    ('React',      date_trunc('month', now())::date - INTERVAL '1 month',  38, 15, 6.0, 25.0, 6.0, 3),
    ('React',      date_trunc('month', now())::date,                       45, 17, 7.0, 27.0, 7.0, 3)
ON CONFLICT (technology_name, month) DO NOTHING;

-- Sample CMS content
INSERT INTO cms_content (id, title, type, content_date, status, created_at, updated_at)
VALUES
    (gen_random_uuid(), 'Top công nghệ Q2/2026', 'Report',  date_trunc('month', now())::date, 'Published', now(), now()),
    (gen_random_uuid(), 'Tuyển dụng Backend tăng mạnh', 'Job', date_trunc('month', now())::date, 'Analyzed', now(), now()),
    (gen_random_uuid(), 'Từ khóa nổi bật: AI, RAG', 'Keyword', date_trunc('month', now())::date, 'Pending', now(), now())
ON CONFLICT DO NOTHING;
