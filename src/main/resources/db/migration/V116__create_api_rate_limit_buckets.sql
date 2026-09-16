CREATE TABLE api_rate_limit_buckets (
    policy_code VARCHAR(64) NOT NULL,
    subject_hash VARCHAR(64) NOT NULL,
    window_started_at TIMESTAMPTZ NOT NULL,
    request_count INTEGER NOT NULL CHECK (request_count >= 0),
    expires_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (policy_code, subject_hash, window_started_at)
);

CREATE INDEX idx_api_rate_limit_buckets_expires_at
    ON api_rate_limit_buckets (expires_at);
