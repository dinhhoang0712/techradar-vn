-- Trend alert fan-out (TrendAlertDispatcher.onTrendAlert -> findTrendSubscribers) filters
-- user_profile by array containment on every Kafka trend.alerts message. Without an index
-- this is a sequential scan of user_profile per alert.
CREATE INDEX IF NOT EXISTS idx_user_profile_technologies_gin
    ON user_profile USING GIN (technologies);
