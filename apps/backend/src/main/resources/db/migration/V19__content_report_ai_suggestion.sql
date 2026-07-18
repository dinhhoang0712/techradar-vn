-- Cached LLM moderation suggestion per report — computed on demand (admin clicks "Gợi ý AI"),
-- not automatically, so we don't spend LLM calls on reports nobody reviews.

ALTER TABLE content_report
    ADD COLUMN IF NOT EXISTS ai_suggested_action VARCHAR(20),
    ADD COLUMN IF NOT EXISTS ai_suggested_reason TEXT,
    ADD COLUMN IF NOT EXISTS ai_confidence DOUBLE PRECISION,
    ADD COLUMN IF NOT EXISTS ai_suggested_at TIMESTAMP;

ALTER TABLE content_report
    ADD CONSTRAINT chk_ai_suggested_action
    CHECK (ai_suggested_action IS NULL OR ai_suggested_action IN ('REMOVE', 'DISMISS'));
