-- User-submitted reports (flags) on posts/comments, reviewed by admins in the moderation queue.
-- Deleting the reported post/comment cascades away its reports too (nothing left to review).

CREATE TABLE IF NOT EXISTS content_report (
    id          UUID PRIMARY KEY,
    reporter_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    post_id     UUID REFERENCES post(id) ON DELETE CASCADE,
    comment_id  UUID REFERENCES post_comment(id) ON DELETE CASCADE,
    reason      TEXT NOT NULL,
    status      VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    created_at  TIMESTAMP NOT NULL DEFAULT now(),
    resolved_at TIMESTAMP,
    resolved_by UUID REFERENCES users(id) ON DELETE SET NULL,
    CHECK (status IN ('PENDING', 'DISMISSED')),
    CHECK ((post_id IS NOT NULL AND comment_id IS NULL) OR (post_id IS NULL AND comment_id IS NOT NULL))
);

CREATE INDEX IF NOT EXISTS idx_report_status ON content_report(status, created_at);

-- A user can only report the same post/comment once (repeat clicks are a no-op, not new rows).
CREATE UNIQUE INDEX IF NOT EXISTS uq_report_reporter_post
    ON content_report(reporter_id, post_id) WHERE post_id IS NOT NULL;
CREATE UNIQUE INDEX IF NOT EXISTS uq_report_reporter_comment
    ON content_report(reporter_id, comment_id) WHERE comment_id IS NOT NULL;
