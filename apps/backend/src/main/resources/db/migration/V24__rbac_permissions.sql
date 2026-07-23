-- Permission-based RBAC on top of the existing flat users.role column, plus a security stamp
-- for immediate token revocation when an admin changes a user's role/status.
--
-- users.role stays a plain VARCHAR (no FK-by-id churn across every User.getRole() call site) but
-- now references roles(code), so it must always name a real role.

CREATE TABLE IF NOT EXISTS roles (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    code        VARCHAR(50)  NOT NULL UNIQUE,
    name        VARCHAR(100) NOT NULL,
    description TEXT
);

CREATE TABLE IF NOT EXISTS permissions (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    code        VARCHAR(100) NOT NULL UNIQUE,
    description TEXT
);

CREATE TABLE IF NOT EXISTS role_permissions (
    role_id       UUID NOT NULL REFERENCES roles(id) ON DELETE CASCADE,
    permission_id UUID NOT NULL REFERENCES permissions(id) ON DELETE CASCADE,
    PRIMARY KEY (role_id, permission_id)
);

INSERT INTO roles (code, name, description) VALUES
    ('user',  'User',  'Standard authenticated user - no admin permissions'),
    ('admin', 'Admin', 'Full administrative access - every admin permission')
ON CONFLICT (code) DO NOTHING;

INSERT INTO permissions (code, description) VALUES
    ('user:manage',         'Create/update/delete/list any user account (UserAdminController)'),
    ('notification:manage', 'Send/broadcast admin notifications (AdminNotificationController)'),
    ('analytics:manage',    'Rebuild radar/compare analytics from Neo4j (AnalyticsAdminController)'),
    ('cms:manage',          'Create/update/delete CMS content (AdminCmsController)'),
    ('crawler:manage',      'Trigger crawl runs / view crawl status (CrawlerAdminController)'),
    ('cache:manage',        'Evict application caches (CacheAdminController)'),
    ('system:settings',     'View/update/delete application settings (AdminController)'),
    ('datapipeline:manage', 'Trigger/inspect data-platform gold jobs (AdminDataPlatformController)'),
    ('social:moderate',     'Moderate posts/comments/reports (AdminSocialController)'),
    ('audit:view',          'List audit log entries (AuditLogAdminController)'),
    ('dashboard:view',      'View admin dashboard metrics (AdminDashboardController)'),
    ('clustering:manage',   'Trigger/inspect clustering pipeline, override cluster labels (AdminClusteringController)')
ON CONFLICT (code) DO NOTHING;

-- ADMIN gets every permission that used to be gated solely by hasRole('ADMIN'), so this
-- migration preserves current behavior exactly. USER intentionally gets none: self-service
-- endpoints already scope to the caller's own id via SecurityUtils.currentUserId(), not RBAC.
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r, permissions p WHERE r.code = 'admin'
ON CONFLICT DO NOTHING;

ALTER TABLE users
    ADD CONSTRAINT fk_users_role FOREIGN KEY (role) REFERENCES roles(code);

ALTER TABLE users
    ADD COLUMN IF NOT EXISTS security_stamp UUID NOT NULL DEFAULT gen_random_uuid();
