-- Avatar storage (in-DB bytes) and password-reset tokens.

CREATE TABLE IF NOT EXISTS user_avatar (
    user_id      UUID PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
    content_type VARCHAR(100) NOT NULL DEFAULT 'image/png',
    data         BYTEA        NOT NULL,
    updated_at   TIMESTAMP    NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS password_reset (
    token      UUID PRIMARY KEY,
    user_id    UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    expires_at TIMESTAMP NOT NULL,
    used       BOOLEAN   NOT NULL DEFAULT false,
    created_at TIMESTAMP NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_password_reset_user ON password_reset(user_id);
