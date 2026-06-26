-- Activity tracking (powers /admin/dashboard visits/searches/keywords) and CMS content.

CREATE TABLE IF NOT EXISTS activity_log (
    id         BIGSERIAL PRIMARY KEY,
    type       VARCHAR(20) NOT NULL,   -- 'visit' | 'search'
    user_id    UUID,
    path       TEXT,
    keyword    TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_activity_type_time ON activity_log(type, created_at);
CREATE INDEX IF NOT EXISTS idx_activity_keyword ON activity_log(keyword) WHERE keyword IS NOT NULL;

CREATE TABLE IF NOT EXISTS cms_content (
    id           UUID PRIMARY KEY,
    title        VARCHAR(500) NOT NULL,
    type         VARCHAR(50),            -- Report | Job | Keyword
    content_date DATE,
    status       VARCHAR(50) NOT NULL DEFAULT 'Pending',
    created_at   TIMESTAMP NOT NULL DEFAULT now(),
    updated_at   TIMESTAMP NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_cms_created ON cms_content(created_at DESC);
