CREATE TABLE IF NOT EXISTS post_image (
    id           UUID PRIMARY KEY,
    post_id      UUID NOT NULL REFERENCES post(id) ON DELETE CASCADE,
    ordinal      INT NOT NULL,
    content_type VARCHAR(100) NOT NULL,
    data         BYTEA NOT NULL,
    created_at   TIMESTAMP NOT NULL DEFAULT now(),
    UNIQUE (post_id, ordinal)
);
CREATE INDEX IF NOT EXISTS idx_post_image_post ON post_image(post_id, ordinal);
