CREATE TABLE IF NOT EXISTS identity_liveness_sessions (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    provider VARCHAR(80) NOT NULL,
    provider_session_id VARCHAR(255),
    status VARCHAR(30) NOT NULL,
    purpose VARCHAR(30) NOT NULL,
    provider_message VARCHAR(500),
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    verified_at TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_identity_liveness_user
    ON identity_liveness_sessions(user_id);

CREATE UNIQUE INDEX IF NOT EXISTS uq_identity_liveness_provider_session
    ON identity_liveness_sessions(provider_session_id)
    WHERE provider_session_id IS NOT NULL;
