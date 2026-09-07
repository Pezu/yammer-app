-- =============================================================================
-- Yammer seed data — base roles and the default SUPER operator.
-- =============================================================================

-- Base set of roles.
INSERT INTO role (role) VALUES
    ('WAITER'),
    ('BARMAN'),
    ('ADMIN'),
    ('SERVICE'),
    ('SUPER'),
    ('WATCHER')
ON CONFLICT (role) DO NOTHING;

-- Default SUPER operator (no client scope, so it can manage everything).
-- Password "Cinci_55!!" seeded as MD5; AuthService transparently re-hashes it
-- to BCrypt on the first successful login.
INSERT INTO users (username, password, roles)
VALUES ('office@yammer.ro', md5('Cinci_55!!'), ARRAY['SUPER'])
ON CONFLICT (username) DO NOTHING;
