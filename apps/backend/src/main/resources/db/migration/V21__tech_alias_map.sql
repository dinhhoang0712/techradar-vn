-- Technology name canonicalization — nguồn sự thật duy nhất dùng chung giữa
-- EntityExtractionService.java (Kafka realtime) và silver/processor.py
-- (Python data-platform), để "Go"/"Golang", "ML"/"Machine Learning"... không
-- bị tách thành 2 :Technology node khác nhau trong Neo4j.

-- ── Bảng alias chuẩn hoá ────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS dp_tech_alias_map (
    alias_normalized TEXT        PRIMARY KEY,          -- casefold + trim, vd "golang"
    canonical_name   TEXT        NOT NULL,              -- vd "Go"
    source           TEXT        NOT NULL DEFAULT 'seed', -- seed | llm_auto | human_review
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_dp_tech_alias_canonical ON dp_tech_alias_map(canonical_name);

-- ── Hàng chờ duyệt — case LLM không tự tin, cần người xác nhận ─────────────
CREATE TABLE IF NOT EXISTS dp_tech_alias_review_queue (
    id            BIGSERIAL   PRIMARY KEY,
    name_a        TEXT        NOT NULL,
    name_b        TEXT        NOT NULL,
    llm_reasoning TEXT,
    status        TEXT        NOT NULL DEFAULT 'pending', -- pending | approved | rejected
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    decided_at    TIMESTAMPTZ
);

CREATE INDEX IF NOT EXISTS idx_dp_tech_review_status ON dp_tech_alias_review_queue(status);

-- ── Seed: các cặp đồng nghĩa tiếng Anh đã biết trước (đã phát hiện thủ công) ─
-- alias_normalized casefold nên "AWS" và "Aws" cùng khớp 1 dòng "aws" — không
-- cần liệt kê riêng từng biến thể hoa/thường.
INSERT INTO dp_tech_alias_map (alias_normalized, canonical_name, source) VALUES
    ('golang', 'Go', 'seed'),
    ('ml', 'Machine Learning', 'seed'),
    ('aws', 'AWS', 'seed'),
    ('javascript', 'JavaScript', 'seed'),
    ('typescript', 'TypeScript', 'seed'),
    ('microservices', 'Microservices', 'seed'),
    -- Viết tắt / cách viết phổ biến khác — phòng ngừa, chưa chắc đã trùng
    -- trong Neo4j hiện tại nhưng sẽ chặn được nếu crawler sau này gặp.
    ('k8s', 'Kubernetes', 'seed'),
    ('k8', 'Kubernetes', 'seed'),
    ('postgres', 'PostgreSQL', 'seed'),
    ('postgre', 'PostgreSQL', 'seed'),
    ('mongo', 'MongoDB', 'seed'),
    ('js', 'JavaScript', 'seed'),
    ('ts', 'TypeScript', 'seed'),
    ('py', 'Python', 'seed'),
    ('node', 'Node.js', 'seed'),
    ('nodejs', 'Node.js', 'seed'),
    ('reactjs', 'React', 'seed'),
    ('react.js', 'React', 'seed'),
    ('vuejs', 'Vue', 'seed'),
    ('vue.js', 'Vue', 'seed'),
    ('nextjs', 'Next.js', 'seed'),
    ('nuxtjs', 'Nuxt', 'seed'),
    ('expressjs', 'Express', 'seed'),
    ('express.js', 'Express', 'seed'),
    ('reactnative', 'React Native', 'seed'),
    ('react-native', 'React Native', 'seed'),
    ('springboot', 'Spring Boot', 'seed'),
    ('spring-boot', 'Spring Boot', 'seed'),
    ('dotnet', '.NET', 'seed'),
    ('dot net', '.NET', 'seed'),
    ('aspnet', 'ASP.NET', 'seed'),
    ('ci cd', 'CI/CD', 'seed'),
    ('csharp', 'C#', 'seed'),
    ('c sharp', 'C#', 'seed'),
    ('cpp', 'C++', 'seed'),
    ('kube', 'Kubernetes', 'seed'),
    ('angularjs', 'Angular', 'seed'),
    ('angular js', 'Angular', 'seed'),
    ('ruby on rails', 'Rails', 'seed'),
    ('rubyonrails', 'Rails', 'seed'),
    ('ror', 'Rails', 'seed'),
    ('netcore', '.NET', 'seed'),
    ('.net core', '.NET', 'seed'),
    ('fast-api', 'FastAPI', 'seed'),
    ('postgres sql', 'PostgreSQL', 'seed'),
    ('mysql db', 'MySQL', 'seed'),
    ('mongo db', 'MongoDB', 'seed'),
    ('elastic search', 'Elasticsearch', 'seed'),
    ('elastic', 'Elasticsearch', 'seed'),
    ('rabbit mq', 'RabbitMQ', 'seed'),
    ('graph ql', 'GraphQL', 'seed'),
    ('web socket', 'WebSocket', 'seed'),
    ('websockets', 'WebSocket', 'seed'),
    ('microservice', 'Microservices', 'seed'),
    ('micro services', 'Microservices', 'seed'),
    ('chat gpt', 'ChatGPT', 'seed'),
    ('hugging face', 'HuggingFace', 'seed'),
    ('powerbi', 'Power BI', 'seed'),
    ('githubactions', 'GitHub Actions', 'seed'),
    ('gh actions', 'GitHub Actions', 'seed')
ON CONFLICT (alias_normalized) DO NOTHING;
