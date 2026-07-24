-- New permission for the Knowledge Graph review queue (KgReviewAdminController) — approve/reject
-- Technology alias merge candidates from dp_tech_alias_review_queue, and review/merge Company
-- near-duplicate groups detected live from Neo4j. Same pattern as V27 (graph:manage): V24's
-- admin grant-all only covered permissions that existed at that time, so a permission added
-- later must grant itself here.

INSERT INTO permissions (code, description) VALUES
    ('kg:review', 'Review Knowledge Graph dedup queue - approve/reject Technology alias merges, merge Company near-duplicates (KgReviewAdminController)')
ON CONFLICT (code) DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r, permissions p WHERE r.code = 'admin' AND p.code = 'kg:review'
ON CONFLICT DO NOTHING;
