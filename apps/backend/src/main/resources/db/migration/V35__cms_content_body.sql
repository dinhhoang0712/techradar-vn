-- Lets the new monthly report scheduler (MonthlyReportSchedulerService) persist the full generated
-- markdown alongside its cms_content catalog row — previously cms_content only had a title/status
-- placeholder with nowhere to store the actual report body. Nullable: crawler/keyword-digest rows
-- (see JobCompletionNotifier, RadarAnalyticsEtlService) have no body of their own.
ALTER TABLE cms_content ADD COLUMN IF NOT EXISTS body TEXT;
