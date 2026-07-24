-- Technology category classification (language/framework/tool/cloud/database/...).
-- Keyed by canonical_name (not alias_normalized like dp_tech_alias_map) so category stays
-- consistent across every alias of the same technology instead of duplicating/drifting per
-- alias row.
CREATE TABLE IF NOT EXISTS dp_tech_category (
    canonical_name TEXT PRIMARY KEY,
    category       TEXT NOT NULL,
    source         TEXT NOT NULL DEFAULT 'llm_auto', -- llm_auto | human_review
    updated_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);
