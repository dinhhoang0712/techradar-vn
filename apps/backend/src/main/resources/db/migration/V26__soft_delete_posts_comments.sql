-- Switches post/comment deletion (both owner self-delete and admin moderation delete) from a hard
-- DELETE to a soft tombstone, so moderation evidence (content_report rows referencing a post/
-- comment) is no longer destroyed by ON DELETE CASCADE the moment the content is removed.

ALTER TABLE post ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMP;
ALTER TABLE post_comment ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMP;

-- Read paths filter WHERE deleted_at IS NULL; these indexes keep that filter cheap.
CREATE INDEX IF NOT EXISTS idx_post_deleted_at ON post(deleted_at) WHERE deleted_at IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_post_comment_deleted_at ON post_comment(deleted_at) WHERE deleted_at IS NOT NULL;
