-- chk_activity_type (V4) only allowed 'visit'/'search'. AiProxyRequestHandler.recordAiRequest()
-- has been inserting type='ai_request' ever since the AI-proxy consolidation, but every single
-- insert has been silently rejected by this constraint and swallowed by the best-effort
-- .onErrorResume() — so the admin live-metrics "Request AI hôm nay" tile has always read 0.
ALTER TABLE activity_log DROP CONSTRAINT IF EXISTS chk_activity_type;
ALTER TABLE activity_log ADD CONSTRAINT chk_activity_type CHECK (type IN ('visit', 'search', 'ai_request'));
