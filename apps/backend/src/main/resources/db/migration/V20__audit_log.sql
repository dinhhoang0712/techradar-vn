-- Append-only audit trail for admin-triggered mutations (user CRUD, content moderation,
-- cluster label overrides, pipeline triggers, admin notifications, ...) — governance:
-- who did what, when. No FK to users(id): the trail must outlive a deleted actor account.

CREATE TABLE IF NOT EXISTS audit_log (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    actor_id    UUID NOT NULL,
    action      VARCHAR(50) NOT NULL,
    target_type VARCHAR(50),
    target_id   VARCHAR(100),
    details     TEXT,
    created_at  TIMESTAMP NOT NULL DEFAULT now()
);

-- Newest-first listing is the only read pattern the admin UI needs.
CREATE INDEX IF NOT EXISTS idx_audit_log_created_at ON audit_log(created_at DESC);
CREATE INDEX IF NOT EXISTS idx_audit_log_actor ON audit_log(actor_id);
