-- Closes a real validation gap: users.status/subscription_tier and cms_content.status/type were
-- never restricted to a fixed vocabulary at any layer (no Bean Validation, no CHECK). See
-- docs/adr/0010-oneof-validation-for-fixed-vocabulary-strings.md.
--
-- Canonical vocabulary for users.status/subscription_tier is UPPERCASE, matching the documented
-- API contract in docs/API_DOCs_v1.md (ACTIVE/INACTIVE/SUSPENDED, FREE/PRO/ENTERPRISE) rather than
-- the lowercase values application code had been writing ("active"/"free") — backfill existing
-- rows before adding the CHECK, or this migration would fail against any already-seeded data.
UPDATE users SET status = UPPER(status) WHERE status IS NOT NULL;
UPDATE users SET subscription_tier = UPPER(subscription_tier) WHERE subscription_tier IS NOT NULL;

ALTER TABLE users ALTER COLUMN status SET DEFAULT 'ACTIVE';
ALTER TABLE users ALTER COLUMN subscription_tier SET DEFAULT 'FREE';

ALTER TABLE users
    ADD CONSTRAINT chk_users_status CHECK (status IN ('ACTIVE', 'INACTIVE', 'SUSPENDED'));
ALTER TABLE users
    ADD CONSTRAINT chk_users_subscription_tier CHECK (subscription_tier IN ('FREE', 'PRO', 'ENTERPRISE'));

-- cms_content.status/type already only ever written as this exact Title-Case vocabulary
-- everywhere in application code (CmsService, MonthlyReportSchedulerService, RadarAnalyticsEtlService,
-- JobCompletionNotifier) - no backfill needed, just closing the gap that nothing enforced it.
ALTER TABLE cms_content
    ADD CONSTRAINT chk_cms_content_status CHECK (status IN ('Published', 'Analyzed', 'Pending', 'Archived'));
ALTER TABLE cms_content
    ADD CONSTRAINT chk_cms_content_type CHECK (type IN ('Report', 'Job', 'Keyword'));
