-- In-app + email notifications. First producer: trend alerts (radar ETL).

CREATE TABLE IF NOT EXISTS notification (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id    UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    type       VARCHAR(40)  NOT NULL,
    title      VARCHAR(200) NOT NULL,
    body       TEXT,
    link       VARCHAR(300),
    is_read    BOOLEAN      NOT NULL DEFAULT false,
    created_at TIMESTAMP    NOT NULL DEFAULT now()
);

-- Unread-first listing per user.
CREATE INDEX IF NOT EXISTS idx_notification_user
    ON notification(user_id, is_read, created_at DESC);

-- Per-user channel preferences (default on). Drives the trend-alert dispatcher.
ALTER TABLE user_profile ADD COLUMN IF NOT EXISTS notify_inapp BOOLEAN NOT NULL DEFAULT true;
ALTER TABLE user_profile ADD COLUMN IF NOT EXISTS notify_email BOOLEAN NOT NULL DEFAULT true;
