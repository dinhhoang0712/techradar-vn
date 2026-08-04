-- dp_processed_jobs.level chưa từng bị enforce ở bất kỳ layer nào (crawler gửi free-text
-- tiếng Việt hoặc rỗng) — backfill mọi giá trị hiện có không khớp enum về NULL trước khi
-- thêm CHECK, để migration không fail trên data cũ/live (đúng pattern V37).
UPDATE dp_processed_jobs
SET level = NULL
WHERE level IS NOT NULL
  AND level NOT IN ('Intern', 'Fresher', 'Junior', 'Middle', 'Senior', 'Lead');

ALTER TABLE dp_processed_jobs
    ADD CONSTRAINT chk_dp_processed_jobs_level
    CHECK (level IS NULL OR level IN ('Intern', 'Fresher', 'Junior', 'Middle', 'Senior', 'Lead'));
