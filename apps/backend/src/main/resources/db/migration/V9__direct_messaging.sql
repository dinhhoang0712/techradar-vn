-- 1-1 direct messaging. Real-time delivery is push-based via an in-memory SSE broadcaster
-- (single backend instance, per docker-compose — no Redis pub/sub needed).

CREATE TABLE IF NOT EXISTS conversation (
    id         UUID PRIMARY KEY,
    user_a_id  UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    user_b_id  UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    CHECK (user_a_id < user_b_id),
    UNIQUE (user_a_id, user_b_id)
);
CREATE INDEX IF NOT EXISTS idx_conversation_user_a ON conversation(user_a_id);
CREATE INDEX IF NOT EXISTS idx_conversation_user_b ON conversation(user_b_id);

CREATE TABLE IF NOT EXISTS direct_message (
    id              UUID PRIMARY KEY,
    conversation_id UUID NOT NULL REFERENCES conversation(id) ON DELETE CASCADE,
    sender_id       UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    content         TEXT NOT NULL,
    created_at      TIMESTAMP NOT NULL DEFAULT now(),
    read_at         TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_dm_conversation ON direct_message(conversation_id, created_at);
