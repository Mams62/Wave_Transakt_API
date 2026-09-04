CREATE TABLE verification_codes (
                                    id UUID PRIMARY KEY,
                                    user_id UUID NOT NULL,
                                    code VARCHAR(10) NOT NULL,
                                    type VARCHAR(50) NOT NULL,
                                    expires_at TIMESTAMP NOT NULL,
                                    used BOOLEAN NOT NULL DEFAULT FALSE,
                                    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

                                    CONSTRAINT fk_verification_codes_user
                                        FOREIGN KEY (user_id)
                                            REFERENCES users(id)
                                            ON DELETE CASCADE
);