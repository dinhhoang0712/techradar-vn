-- Dev-only: more demo users (AdminUsers list looked sparse with just admin+1 demo)
-- and more CMS content (AdminCMS calls the real API now, not mocked, per V3).
-- Applied only under the dev Flyway location (not in prod).

-- More demo users: mix of role/status/subscription_tier so filters/badges have variety.
INSERT INTO users (id, email, password_hash, full_name, role, status, subscription_tier, created_at, updated_at)
VALUES
    ('00000000-0000-0000-0000-000000000010', 'lan.nguyen@techradar.vn',  crypt('User@12345', gen_salt('bf', 10)), 'Lan Nguyen',   'user',  'active',  'pro',  now(), now()),
    ('00000000-0000-0000-0000-000000000011', 'minh.tran@techradar.vn',   crypt('User@12345', gen_salt('bf', 10)), 'Minh Tran',    'user',  'active',  'free', now(), now()),
    ('00000000-0000-0000-0000-000000000012', 'hoa.le@techradar.vn',      crypt('User@12345', gen_salt('bf', 10)), 'Hoa Le',       'user',  'blocked', 'free', now(), now()),
    ('00000000-0000-0000-0000-000000000013', 'duc.pham@techradar.vn',    crypt('User@12345', gen_salt('bf', 10)), 'Duc Pham',     'user',  'active',  'pro',  now(), now()),
    ('00000000-0000-0000-0000-000000000014', 'mai.vo@techradar.vn',      crypt('Admin@12345', gen_salt('bf', 10)), 'Mai Vo',      'admin', 'active',  'pro',  now(), now()),
    ('00000000-0000-0000-0000-000000000015', 'khanh.do@techradar.vn',    crypt('User@12345', gen_salt('bf', 10)), 'Khanh Do',     'user',  'active',  'free', now(), now()),
    ('00000000-0000-0000-0000-000000000016', 'thao.bui@techradar.vn',    crypt('User@12345', gen_salt('bf', 10)), 'Thao Bui',     'user',  'blocked', 'free', now(), now())
ON CONFLICT (email) DO NOTHING;

INSERT INTO user_profile (user_id, job_role, technologies, location, bio)
VALUES
    ('00000000-0000-0000-0000-000000000010', 'Frontend Engineer', ARRAY['React', 'TypeScript', 'Vue'], 'Ho Chi Minh', 'Thich xay dung UI mixed voi design system'),
    ('00000000-0000-0000-0000-000000000011', 'Data Engineer',     ARRAY['Python', 'Spark', 'Kafka'],   'Da Nang',     'Lam viec voi data pipeline quy mo lon'),
    ('00000000-0000-0000-0000-000000000013', 'DevOps Engineer',   ARRAY['Docker', 'Kubernetes', 'AWS'],'Ha Noi',      'Van hanh he thong cloud-native'),
    ('00000000-0000-0000-0000-000000000015', 'AI Engineer',       ARRAY['Python', 'PyTorch', 'NLP'],   'Ho Chi Minh', 'Nghien cuu ung dung LLM/RAG')
ON CONFLICT (user_id) DO NOTHING;

-- More CMS content across Report / Job / Keyword, spread over the last 6 months.
INSERT INTO cms_content (id, title, type, content_date, status, created_at, updated_at)
VALUES
    (gen_random_uuid(), 'Báo cáo xu hướng công nghệ tháng 2/2026',        'Report',  date_trunc('month', now())::date - INTERVAL '5 months', 'Published', now(), now()),
    (gen_random_uuid(), 'Báo cáo xu hướng công nghệ tháng 3/2026',        'Report',  date_trunc('month', now())::date - INTERVAL '4 months', 'Published', now(), now()),
    (gen_random_uuid(), 'Báo cáo xu hướng công nghệ tháng 4/2026',        'Report',  date_trunc('month', now())::date - INTERVAL '3 months', 'Published', now(), now()),
    (gen_random_uuid(), 'Báo cáo xu hướng công nghệ tháng 5/2026',        'Report',  date_trunc('month', now())::date - INTERVAL '2 months', 'Published', now(), now()),
    (gen_random_uuid(), 'Báo cáo xu hướng công nghệ tháng 6/2026',        'Report',  date_trunc('month', now())::date - INTERVAL '1 month',  'Analyzed',  now(), now()),
    (gen_random_uuid(), 'Báo cáo xu hướng công nghệ tháng 7/2026',        'Report',  date_trunc('month', now())::date,                       'Pending',   now(), now()),
    (gen_random_uuid(), 'So sánh mức lương Backend vs Frontend 2026',     'Report',  date_trunc('month', now())::date - INTERVAL '2 months', 'Published', now(), now()),
    (gen_random_uuid(), 'Toàn cảnh thị trường AI Engineer Việt Nam',      'Report',  date_trunc('month', now())::date - INTERVAL '1 month',  'Published', now(), now()),

    (gen_random_uuid(), 'Tuyển dụng Backend tăng mạnh quý 2/2026',        'Job',     date_trunc('month', now())::date - INTERVAL '3 months', 'Analyzed',  now(), now()),
    (gen_random_uuid(), 'Nhu cầu AI Engineer tăng 40% so với cùng kỳ',    'Job',     date_trunc('month', now())::date - INTERVAL '2 months', 'Analyzed',  now(), now()),
    (gen_random_uuid(), 'DevOps Engineer khan hiếm ứng viên senior',      'Job',     date_trunc('month', now())::date - INTERVAL '1 month',  'Published', now(), now()),
    (gen_random_uuid(), 'Frontend React/Vue vẫn dẫn đầu về số lượng tin',  'Job',     date_trunc('month', now())::date,                       'Pending',   now(), now()),
    (gen_random_uuid(), 'Data Engineer: mức lương tăng theo kinh nghiệm', 'Job',     date_trunc('month', now())::date - INTERVAL '4 months', 'Published', now(), now()),
    (gen_random_uuid(), 'Golang tiếp tục mở rộng trong tuyển dụng backend','Job',    date_trunc('month', now())::date - INTERVAL '1 month',  'Analyzed',  now(), now()),

    (gen_random_uuid(), 'Từ khóa nổi bật: AI, RAG',                       'Keyword', date_trunc('month', now())::date,                       'Pending',   now(), now()),
    (gen_random_uuid(), 'Từ khóa nổi bật: Kubernetes, Cloud Native',      'Keyword', date_trunc('month', now())::date - INTERVAL '1 month',  'Analyzed',  now(), now()),
    (gen_random_uuid(), 'Từ khóa nổi bật: TypeScript, Next.js',           'Keyword', date_trunc('month', now())::date - INTERVAL '2 months', 'Published', now(), now()),
    (gen_random_uuid(), 'Từ khóa nổi bật: LLM, Vector Database',          'Keyword', date_trunc('month', now())::date - INTERVAL '1 month',  'Published', now(), now()),
    (gen_random_uuid(), 'Từ khóa nổi bật: Microservices, Kafka',          'Keyword', date_trunc('month', now())::date - INTERVAL '3 months', 'Analyzed',  now(), now()),
    (gen_random_uuid(), 'Từ khóa nổi bật: Rust, Systems Programming',     'Keyword', date_trunc('month', now())::date - INTERVAL '2 months', 'Pending',   now(), now())
ON CONFLICT DO NOTHING;
