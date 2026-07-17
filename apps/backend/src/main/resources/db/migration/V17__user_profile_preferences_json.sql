-- ai-rag-core reads/writes user_profile.preferences_json for long-term memory and
-- LLM personalization (recommend/career), but that column was never added on the
-- Java side — every read/write from ai-rag-core silently fails.
ALTER TABLE user_profile ADD COLUMN IF NOT EXISTS preferences_json JSONB;
