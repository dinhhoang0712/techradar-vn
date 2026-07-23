-- New permission for the GDS-based graph analytics rebuild (GraphAnalyticsAdminController).
-- V24's admin grant-all only ran once, over the permissions that existed back then - it does not
-- retroactively cover a permission inserted by a later migration, so this file grants it directly.

INSERT INTO permissions (code, description) VALUES
    ('graph:manage', 'Trigger GDS graph-analytics rebuild - PageRank/Louvain/degree centrality (GraphAnalyticsAdminController)')
ON CONFLICT (code) DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r, permissions p WHERE r.code = 'admin' AND p.code = 'graph:manage'
ON CONFLICT DO NOTHING;
