-- Reconcile the user schema with what the ai-rag-core service and the web/mobile clients expect:
-- users.full_name + a user_profile table (job_role, technologies[], location, bio, avatar_url).

ALTER TABLE users ADD COLUMN IF NOT EXISTS full_name VARCHAR(255);

CREATE TABLE IF NOT EXISTS user_profile (
    user_id      UUID PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
    job_role     VARCHAR(255),
    technologies TEXT[],
    location     VARCHAR(255),
    bio          TEXT,
    avatar_url   TEXT,
    updated_at   TIMESTAMP NOT NULL DEFAULT now()
);
