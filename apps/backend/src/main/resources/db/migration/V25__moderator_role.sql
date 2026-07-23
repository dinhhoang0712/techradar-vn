-- Proves out the RBAC design from V24: a narrower role can be added and granted a subset of
-- admin permissions with pure data (no code change) - AdminUserService.normalizeRole() validates
-- role names against this table instead of a hardcoded admin/user binary.

INSERT INTO roles (code, name, description) VALUES
    ('moderator', 'Moderator', 'Can moderate social content and view the audit log - nothing else')
ON CONFLICT (code) DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r, permissions p
WHERE r.code = 'moderator' AND p.code IN ('social:moderate', 'audit:view')
ON CONFLICT DO NOTHING;
