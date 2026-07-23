-- Career GPS: persist the user's current top roadmap recommendations ("skills they're actively
-- learning next", computed by GetCareerRoadmapUseCase) so job-match alerts (JobMatchDispatcher)
-- can fan out on them too, not just on user_profile.technologies. Mirrors that column + its GIN
-- index (V2__user_profile.sql, V10__user_profile_technologies_gin_index.sql) exactly.
ALTER TABLE user_profile ADD COLUMN IF NOT EXISTS target_skills TEXT[];

CREATE INDEX IF NOT EXISTS idx_user_profile_target_skills_gin
    ON user_profile USING GIN (target_skills);
