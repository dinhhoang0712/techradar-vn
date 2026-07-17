ALTER TABLE post_comment ADD COLUMN IF NOT EXISTS parent_comment_id UUID REFERENCES post_comment(id) ON DELETE CASCADE;
CREATE INDEX IF NOT EXISTS idx_comment_parent ON post_comment(parent_comment_id);
