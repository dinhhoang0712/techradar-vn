-- Dev-only traffic/search history so AdminDashboard (visits-today, searches-today,
-- monthly-visits, top-keywords) shows a realistic trend instead of all zeros.
-- Applied only under the dev Flyway location (not in prod).

-- Visit history for the last 7 months (growing trend), spread across common pages.
INSERT INTO activity_log (type, path, created_at)
SELECT
    'visit',
    (ARRAY['/dashboard', '/radar', '/compare', '/graph', '/chatbot'])[1 + floor(random() * 5)::int],
    date_trunc('month', now()) - (m.months_ago || ' months')::interval + (random() * interval '27 days')
FROM (VALUES (6, 40), (5, 55), (4, 70), (3, 90), (2, 120), (1, 150), (0, 60)) AS m(months_ago, visit_count)
CROSS JOIN LATERAL generate_series(1, m.visit_count) AS i;

-- A handful of visits within today so /admin/dashboard/visits-today is non-zero
-- regardless of what time "today" falls at in the month bucket above.
INSERT INTO activity_log (type, path, created_at)
SELECT 'visit', '/dashboard', now() - (i || ' minutes')::interval
FROM generate_series(1, 12) AS i;

-- Search keywords over the last 60 days, weighted so top-keywords has a clear ranking.
INSERT INTO activity_log (type, keyword, created_at)
SELECT
    'search',
    k.keyword,
    now() - (random() * interval '60 days')
FROM (VALUES
    ('Python', 30), ('Java', 22), ('JavaScript', 18),
    ('React', 15), ('Go', 10), ('AI', 8), ('DevOps', 5)
) AS k(keyword, search_count)
CROSS JOIN LATERAL generate_series(1, k.search_count) AS i;

-- A handful of searches within today so /admin/dashboard/searches-today is non-zero.
INSERT INTO activity_log (type, keyword, created_at)
SELECT 'search', (ARRAY['Python', 'Java', 'React'])[1 + floor(random() * 3)::int], now() - (i || ' minutes')::interval
FROM generate_series(1, 8) AS i;
