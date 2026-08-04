-- Transactional outbox: a business write (e.g. tech_analytics upsert) and the outbox row for its
-- resulting domain event commit inside the SAME R2DBC transaction, so a crash or Kafka outage
-- between "business write committed" and "event actually published" can no longer silently drop
-- the event. A separate relay poller (OutboxRelayScheduler) publishes PENDING rows to Kafka and
-- marks them PUBLISHED, retrying FAILED rows up to a configured attempt limit.
-- See docs/adr/0005-transactional-outbox-trend-alerts.md.

CREATE TABLE IF NOT EXISTS outbox_event (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    topic        VARCHAR(100) NOT NULL,
    payload      TEXT NOT NULL,
    status       VARCHAR(20) NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING', 'PUBLISHED', 'FAILED')),
    attempts     INT NOT NULL DEFAULT 0,
    last_error   TEXT,
    created_at   TIMESTAMP NOT NULL DEFAULT now(),
    published_at TIMESTAMP
);

-- Relay poller's read pattern: oldest-unpublished-first, scoped to PENDING/retryable-FAILED.
CREATE INDEX IF NOT EXISTS idx_outbox_event_status_created ON outbox_event(status, created_at);
