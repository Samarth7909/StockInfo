-- Users and roles
CREATE TABLE IF NOT EXISTS users (
    id               TEXT PRIMARY KEY,
    username         TEXT NOT NULL UNIQUE,
    password_hash    TEXT NOT NULL,
    role             TEXT NOT NULL CHECK(role IN ('SUPPORT','INVESTIGATOR','OPS_LEAD','AUDITOR')),
    full_name        TEXT,
    desk_id          TEXT,
    created_at       TEXT NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%SZ','now'))
);

-- Client access scopes (which clients a user may see)
CREATE TABLE IF NOT EXISTS user_client_scopes (
    user_id          TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    client_id        TEXT NOT NULL,
    PRIMARY KEY (user_id, client_id)
);

-- Indexes
CREATE INDEX IF NOT EXISTS idx_users_username ON users(username);
CREATE INDEX IF NOT EXISTS idx_user_client_scopes_user ON user_client_scopes(user_id);
