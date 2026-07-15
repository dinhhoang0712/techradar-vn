-- Social feed: posts, follows, likes, comments.

CREATE TABLE IF NOT EXISTS post (
    id         UUID PRIMARY KEY,
    user_id    UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    content    TEXT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_post_created ON post(created_at DESC);
CREATE INDEX IF NOT EXISTS idx_post_user ON post(user_id, created_at DESC);

CREATE TABLE IF NOT EXISTS follow (
    follower_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    followee_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    created_at  TIMESTAMP NOT NULL DEFAULT now(),
    PRIMARY KEY (follower_id, followee_id),
    CHECK (follower_id <> followee_id)
);
CREATE INDEX IF NOT EXISTS idx_follow_followee ON follow(followee_id);

CREATE TABLE IF NOT EXISTS post_like (
    post_id    UUID NOT NULL REFERENCES post(id) ON DELETE CASCADE,
    user_id    UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    PRIMARY KEY (post_id, user_id)
);

CREATE TABLE IF NOT EXISTS post_comment (
    id         UUID PRIMARY KEY,
    post_id    UUID NOT NULL REFERENCES post(id) ON DELETE CASCADE,
    user_id    UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    content    TEXT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_comment_post ON post_comment(post_id, created_at);
