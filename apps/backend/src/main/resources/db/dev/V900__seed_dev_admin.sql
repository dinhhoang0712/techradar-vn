-- Dev-only bootstrap admin account. NOT applied in the prod profile.
-- Email:    admin@techradar.vn
-- Password: Admin@12345  (change immediately outside local development)
--
-- pgcrypto's crypt()+gen_salt('bf', 10) produces a $2a$ bcrypt hash that is
-- verified correctly by Spring's BCryptPasswordEncoder.
INSERT INTO users (id, email, password_hash, role, status, subscription_tier, created_at, updated_at)
VALUES (
    '00000000-0000-0000-0000-000000000001',
    'admin@techradar.vn',
    crypt('Admin@12345', gen_salt('bf', 10)),
    'admin',
    'active',
    'pro',
    now(),
    now()
)
ON CONFLICT (email) DO NOTHING;
