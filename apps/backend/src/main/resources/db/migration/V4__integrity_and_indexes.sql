-- V4: data-integrity constraints, a functional index for radar/compare lookups, and an
-- updated_at trigger so timestamps stay correct no matter which service writes the row.

-- ---- CHECK constraints (only on fully app-controlled enums) -----------------
ALTER TABLE users
    ADD CONSTRAINT chk_users_role CHECK (role IN ('user', 'admin', 'moderator'));

ALTER TABLE chat_message
    ADD CONSTRAINT chk_chat_message_role CHECK (role IN ('user', 'assistant', 'system'));

ALTER TABLE activity_log
    ADD CONSTRAINT chk_activity_type CHECK (type IN ('visit', 'search'));

-- ---- Indexes ----------------------------------------------------------------
-- radar/compare search filters on lower(technology_name) = ANY(...)
CREATE INDEX IF NOT EXISTS idx_tech_analytics_name_lower ON tech_analytics (lower(technology_name));
-- admin user listing / role filtering
CREATE INDEX IF NOT EXISTS idx_users_role ON users (role);

-- ---- Keep updated_at fresh on every UPDATE ----------------------------------
CREATE OR REPLACE FUNCTION set_updated_at() RETURNS trigger AS $$
BEGIN
    NEW.updated_at = now();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_users_updated_at ON users;
CREATE TRIGGER trg_users_updated_at BEFORE UPDATE ON users
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

DROP TRIGGER IF EXISTS trg_user_profile_updated_at ON user_profile;
CREATE TRIGGER trg_user_profile_updated_at BEFORE UPDATE ON user_profile
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

DROP TRIGGER IF EXISTS trg_chat_session_updated_at ON chat_session;
CREATE TRIGGER trg_chat_session_updated_at BEFORE UPDATE ON chat_session
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

DROP TRIGGER IF EXISTS trg_settings_updated_at ON settings;
CREATE TRIGGER trg_settings_updated_at BEFORE UPDATE ON settings
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

DROP TRIGGER IF EXISTS trg_cms_content_updated_at ON cms_content;
CREATE TRIGGER trg_cms_content_updated_at BEFORE UPDATE ON cms_content
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();
