CREATE TABLE password_reset_tokens (
                                       id UUID PRIMARY KEY,
                                       user_id UUID NOT NULL,
                                       code VARCHAR(6) NOT NULL,
                                       expires_at TIMESTAMP NOT NULL,
                                       used BOOLEAN NOT NULL DEFAULT FALSE,
                                       created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

                                       CONSTRAINT fk_password_reset_user
                                           FOREIGN KEY (user_id)
                                               REFERENCES users(id)
                                               ON DELETE CASCADE
);

CREATE INDEX idx_password_reset_user_id
    ON password_reset_tokens(user_id);

CREATE INDEX idx_password_reset_code
    ON password_reset_tokens(code);

CREATE INDEX idx_password_reset_expires_at
    ON password_reset_tokens(expires_at);