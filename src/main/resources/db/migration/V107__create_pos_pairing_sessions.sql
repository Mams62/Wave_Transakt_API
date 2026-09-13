CREATE TABLE pos_pairing_codes (
    id UUID PRIMARY KEY,
    terminal_id UUID NOT NULL REFERENCES pos_terminals(id),
    created_by_user_id UUID NOT NULL REFERENCES users(id),
    code_hash VARCHAR(64) NOT NULL UNIQUE,
    expires_at TIMESTAMP NOT NULL,
    consumed_at TIMESTAMP NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_pos_pairing_codes_terminal ON pos_pairing_codes(terminal_id);
CREATE INDEX idx_pos_pairing_codes_expires_at ON pos_pairing_codes(expires_at);

CREATE TABLE pos_terminal_sessions (
    id UUID PRIMARY KEY,
    terminal_id UUID NOT NULL REFERENCES pos_terminals(id),
    token_hash VARCHAR(64) NOT NULL UNIQUE,
    device_label VARCHAR(120) NULL,
    expires_at TIMESTAMP NOT NULL,
    last_seen_at TIMESTAMP NULL,
    revoked_at TIMESTAMP NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_pos_terminal_sessions_terminal ON pos_terminal_sessions(terminal_id);
CREATE INDEX idx_pos_terminal_sessions_expires_at ON pos_terminal_sessions(expires_at);
