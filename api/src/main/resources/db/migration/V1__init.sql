-- =============================================================================
-- Yammer schema baseline — roles, clients and users (the auth slice).
-- =============================================================================

-- =============================================
-- Role  (catalog of assignable role names)
-- =============================================
CREATE TABLE role (
    id   UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    role VARCHAR(100) NOT NULL UNIQUE
);

-- =============================================
-- Client
-- =============================================
CREATE TABLE client (
    id    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name  VARCHAR(255) NOT NULL,
    phone VARCHAR(50),
    email VARCHAR(255)
);

-- =============================================
-- Users  (named "users" — "user" is a reserved word in Postgres)
-- SUPER users have no client; everyone else is scoped to one.
-- =============================================
CREATE TABLE users (
    id        UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    username  VARCHAR(100) NOT NULL UNIQUE,
    name      VARCHAR(255),
    password  VARCHAR(255) NOT NULL,
    phone     VARCHAR(50),
    email     VARCHAR(255),
    roles     TEXT[] NOT NULL DEFAULT '{}',
    client_id UUID REFERENCES client(id)
);
CREATE INDEX idx_users_client_id ON users(client_id);
