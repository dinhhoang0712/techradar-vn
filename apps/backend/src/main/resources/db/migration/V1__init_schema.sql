-- Initial schema for the TechRadar Spring backend.
-- Mirrors the columns used by the R2DBC repositories.

CREATE EXTENSION IF NOT EXISTS pgcrypto;

-- Users -----------------------------------------------------------------
CREATE TABLE IF NOT EXISTS users (
    id                UUID PRIMARY KEY,
    email             VARCHAR(255) NOT NULL UNIQUE,
    password_hash     VARCHAR(255) NOT NULL,
    role              VARCHAR(50)  NOT NULL DEFAULT 'user',
    status            VARCHAR(50)  NOT NULL DEFAULT 'active',
    subscription_tier VARCHAR(50)  NOT NULL DEFAULT 'free',
    created_at        TIMESTAMP    NOT NULL DEFAULT now(),
    updated_at        TIMESTAMP    NOT NULL DEFAULT now()
);

-- Chat sessions / messages ---------------------------------------------
CREATE TABLE IF NOT EXISTS chat_session (
    id            UUID PRIMARY KEY,
    user_id       UUID REFERENCES users(id) ON DELETE CASCADE,
    title         VARCHAR(255),
    model_used    VARCHAR(100),
    system_prompt TEXT,
    created_at    TIMESTAMP NOT NULL DEFAULT now(),
    updated_at    TIMESTAMP NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_chat_session_user ON chat_session(user_id);

CREATE TABLE IF NOT EXISTS chat_message (
    id                UUID PRIMARY KEY,
    session_id        UUID NOT NULL REFERENCES chat_session(id) ON DELETE CASCADE,
    role              VARCHAR(20) NOT NULL,
    content           TEXT        NOT NULL,
    prompt_tokens     INTEGER     DEFAULT 0,
    completion_tokens INTEGER     DEFAULT 0,
    finish_reason     VARCHAR(50),
    created_at        TIMESTAMP   NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_chat_message_session ON chat_message(session_id, created_at);

-- Application settings (feature flags / maintenance) -------------------
CREATE TABLE IF NOT EXISTS settings (
    key         VARCHAR(100) PRIMARY KEY,
    value       TEXT,
    description TEXT,
    updated_at  TIMESTAMP NOT NULL DEFAULT now()
);

-- Radar analytics (read by PostgresRadarAnalyticsRepository) -----------
CREATE TABLE IF NOT EXISTS tech_analytics (
    id              BIGSERIAL PRIMARY KEY,
    technology_name VARCHAR(255) NOT NULL,
    month           DATE         NOT NULL,
    job_count       INTEGER          NOT NULL DEFAULT 0,
    article_count   INTEGER          NOT NULL DEFAULT 0,
    growth_rate     DOUBLE PRECISION NOT NULL DEFAULT 0,
    yoy_growth      DOUBLE PRECISION,
    mom_growth      DOUBLE PRECISION,
    ranking         INTEGER,
    UNIQUE (technology_name, month)
);
CREATE INDEX IF NOT EXISTS idx_tech_analytics_month ON tech_analytics(month);

-- Default feature flags consumed by the web/mobile clients -------------
INSERT INTO settings (key, value, description) VALUES
    ('maintenance_web',    'false', 'Block non-admin web users'),
    ('maintenance_mobile', 'false', 'Block mobile app users'),
    ('feature_graph',      'true',  'Enable Knowledge Graph Explorer'),
    ('feature_chat',       'true',  'Enable AI Chat'),
    ('feature_rag',        'true',  'Enable RAG pipeline')
ON CONFLICT (key) DO NOTHING;
