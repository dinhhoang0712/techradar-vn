-- Career GPS: cho user tự khai "cấp độ hiện tại" (kinh nghiệm) để CareerPage/career_service.py
-- tư vấn lộ trình theo đúng cấp bậc thật, thay vì chỉ match theo chữ trong tên role. Cùng enum
-- 6 mức đã dùng cho dp_processed_jobs.level (V38__job_level_enum.sql).
ALTER TABLE user_profile ADD COLUMN IF NOT EXISTS current_level TEXT;

ALTER TABLE user_profile
    ADD CONSTRAINT chk_user_profile_current_level
    CHECK (current_level IS NULL OR current_level IN ('Intern', 'Fresher', 'Junior', 'Middle', 'Senior', 'Lead'));
