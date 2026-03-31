-- Personal Access Tokens: bearer-token authentication for CLI and API automation.
-- Tokens are SHA-256 hashed before storage; the raw token is shown once at creation.

CREATE TABLE IF NOT EXISTS personal_access_tokens (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    "name" VARCHAR(100) NOT NULL,
    token_hash VARCHAR(64) NOT NULL,
    expires_at TIMESTAMP WITH TIME ZONE NULL,
    last_used_at TIMESTAMP WITH TIME ZONE NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    revoked_at TIMESTAMP WITH TIME ZONE NULL,
    CONSTRAINT personal_access_tokens_token_hash_unique UNIQUE (token_hash),
    CONSTRAINT fk_personal_access_tokens_user_id__id FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE ON UPDATE RESTRICT
);

CREATE INDEX IF NOT EXISTS personal_access_tokens_user_id ON personal_access_tokens (user_id);
