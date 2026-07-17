ALTER TABLE post ADD COLUMN IF NOT EXISTS hashtags TEXT[];

-- Speeds up the feed hashtag filter (`hashtags @> ARRAY[:tag]`). Does NOT speed up trending
-- aggregation, which does unnest()+GROUP BY over recent rows regardless of this index.
CREATE INDEX IF NOT EXISTS idx_post_hashtags_gin ON post USING GIN (hashtags);
