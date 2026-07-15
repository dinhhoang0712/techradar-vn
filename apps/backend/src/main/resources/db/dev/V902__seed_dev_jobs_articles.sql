-- Dev-only sample data for screens not covered by V901: notifications, AI chat,
-- and the data-platform silver tables (processed articles/jobs).
-- Applied only under the dev Flyway location (not in prod).

-- Notifications for the demo user
INSERT INTO notification (id, user_id, type, title, body, link, is_read, created_at)
VALUES
    ('00000000-0000-0000-0000-000000000301',
     '00000000-0000-0000-0000-000000000002',
     'trend_alert', 'Python tăng trưởng mạnh trong tháng',
     'Python vừa vượt mốc top 1 xu hướng tuyển dụng tháng này.',
     '/radar/python', false, now() - INTERVAL '2 days'),
    ('00000000-0000-0000-0000-000000000302',
     '00000000-0000-0000-0000-000000000002',
     'system', 'Chào mừng đến với TechRadar VN',
     'Khám phá radar công nghệ, so sánh xu hướng và trò chuyện với AI.',
     '/dashboard', true, now() - INTERVAL '7 days')
ON CONFLICT (id) DO NOTHING;

-- A demo AI chat session with a short exchange
INSERT INTO chat_session (id, user_id, title, model_used, system_prompt, created_at, updated_at)
VALUES (
    '00000000-0000-0000-0000-000000000401',
    '00000000-0000-0000-0000-000000000002',
    'Xu hướng công nghệ Backend 2026', 'gpt-4o-mini',
    'Bạn là trợ lý phân tích xu hướng công nghệ của TechRadar VN.',
    now() - INTERVAL '1 day', now() - INTERVAL '1 day'
) ON CONFLICT (id) DO NOTHING;

INSERT INTO chat_message (id, session_id, role, content, prompt_tokens, completion_tokens, finish_reason, created_at)
VALUES
    ('00000000-0000-0000-0000-000000000402', '00000000-0000-0000-0000-000000000401',
     'user', 'Công nghệ backend nào đang tăng trưởng nhanh nhất ở Việt Nam?',
     18, 0, NULL, now() - INTERVAL '1 day'),
    ('00000000-0000-0000-0000-000000000403', '00000000-0000-0000-0000-000000000401',
     'assistant', 'Theo dữ liệu radar gần nhất, Python đang dẫn đầu mức tăng trưởng nhờ nhu cầu AI/RAG, theo sau là Java và Go.',
     18, 32, 'stop', now() - INTERVAL '1 day')
ON CONFLICT (id) DO NOTHING;

-- Sample processed articles (data-platform silver layer)
INSERT INTO dp_processed_articles (
    id, source_url, source_platform, title, content, published_at, crawled_at,
    entity_techs, entity_orgs, entity_locs, quality_score, status, processed_at, created_at
)
VALUES
    (md5('https://vnexpress.net/demo/ai-viet-nam-2026'),
     'https://vnexpress.net/demo/ai-viet-nam-2026', 'vnexpress',
     'AI đang thay đổi ngành công nghệ Việt Nam',
     'Nội dung demo: các doanh nghiệp Việt Nam đang đẩy mạnh ứng dụng AI và RAG vào sản phẩm...',
     now() - INTERVAL '10 days', now() - INTERVAL '10 days',
     ARRAY['Python', 'RAG', 'AI'], ARRAY['FPT Software'], ARRAY['Ha Noi'],
     0.85, 'processed', now(), now()),
    (md5('https://cafef.vn/demo/tuyen-dung-backend-tang-manh'),
     'https://cafef.vn/demo/tuyen-dung-backend-tang-manh', 'cafef',
     'Tuyển dụng Backend Engineer tăng mạnh quý 2/2026',
     'Nội dung demo: nhu cầu tuyển dụng Java, Python và Go tiếp tục tăng...',
     now() - INTERVAL '5 days', now() - INTERVAL '5 days',
     ARRAY['Java', 'Go'], ARRAY['VNG', 'Tiki'], ARRAY['Ho Chi Minh'],
     0.78, 'processed', now(), now())
ON CONFLICT (id) DO NOTHING;

-- Sample processed jobs (data-platform silver layer)
INSERT INTO dp_processed_jobs (
    id, source_url, source_platform, job_title, company_name, company_location,
    salary, level, description, requirement, benefit, skills, technologies,
    quality_score, status, processed_at, created_at
)
VALUES
    (md5('https://topdev.vn/demo/backend-engineer-fpt'),
     'https://topdev.vn/demo/backend-engineer-fpt', 'topdev',
     'Backend Engineer (Java/Spring)', 'FPT Software', 'Ha Noi',
     '20-35 triệu', 'Middle',
     'Phát triển và vận hành hệ thống backend cho sản phẩm fintech.',
     '2+ năm kinh nghiệm Java, Spring Boot, PostgreSQL.',
     'Bảo hiểm sức khỏe, thưởng dự án, đào tạo AWS.',
     ARRAY['Java', 'Spring Boot', 'PostgreSQL'], ARRAY['Java', 'Spring'],
     0.82, 'processed', now(), now()),
    (md5('https://itviec.com/demo/python-ai-engineer-vng'),
     'https://itviec.com/demo/python-ai-engineer-vng', 'itviec',
     'AI Engineer (Python/RAG)', 'VNG Corporation', 'Ho Chi Minh',
     '30-50 triệu', 'Senior',
     'Xây dựng pipeline RAG và tích hợp LLM cho sản phẩm nội bộ.',
     '3+ năm kinh nghiệm Python, hiểu biết LLM/RAG/Vector DB.',
     'Lương tháng 13, du lịch công ty, remote linh hoạt.',
     ARRAY['Python', 'RAG', 'LLM'], ARRAY['Python', 'AI'],
     0.9, 'processed', now(), now())
ON CONFLICT (id) DO NOTHING;
