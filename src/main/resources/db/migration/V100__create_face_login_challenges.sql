CREATE TABLE face_login_challenges (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token_hash VARCHAR(64) NOT NULL UNIQUE,
    liveness_session_id UUID,
    expires_at TIMESTAMP NOT NULL,
    consumed_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL
);

CREATE UNIQUE INDEX idx_face_login_token_hash ON face_login_challenges(token_hash);
CREATE INDEX idx_face_login_user ON face_login_challenges(user_id);
