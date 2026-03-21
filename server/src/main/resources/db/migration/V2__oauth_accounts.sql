-- V2: Google OAuth support
-- Allows users to authenticate via Google. OAuth-only users have NULL password_hash.

-- Make password_hash nullable for OAuth-only users
ALTER TABLE users ALTER COLUMN password_hash DROP NOT NULL;

-- OAuth account links (one per user per provider)
CREATE TABLE IF NOT EXISTS oauth_accounts (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    provider VARCHAR(32) NOT NULL,
    provider_user_id VARCHAR(255) NOT NULL,
    email VARCHAR(255) NULL,
    display_name VARCHAR(255) NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_oauth_accounts_user_id__id FOREIGN KEY (user_id)
        REFERENCES users(id) ON DELETE CASCADE ON UPDATE RESTRICT,
    CONSTRAINT oauth_accounts_provider_provider_user_id_unique UNIQUE (provider, provider_user_id),
    CONSTRAINT oauth_accounts_user_id_provider_unique UNIQUE (user_id, provider)
);
CREATE INDEX IF NOT EXISTS oauth_accounts_user_id ON oauth_accounts (user_id);
