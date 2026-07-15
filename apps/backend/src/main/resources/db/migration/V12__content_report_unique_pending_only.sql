-- V11's unique indexes blocked a re-report forever after the first one was dismissed. Only a
-- PENDING report should count as "already reported" — once dismissed, the same user can flag the
-- same target again if the issue recurs.

DROP INDEX IF EXISTS uq_report_reporter_post;
DROP INDEX IF EXISTS uq_report_reporter_comment;

CREATE UNIQUE INDEX IF NOT EXISTS uq_report_reporter_post
    ON content_report(reporter_id, post_id) WHERE post_id IS NOT NULL AND status = 'PENDING';
CREATE UNIQUE INDEX IF NOT EXISTS uq_report_reporter_comment
    ON content_report(reporter_id, comment_id) WHERE comment_id IS NOT NULL AND status = 'PENDING';
