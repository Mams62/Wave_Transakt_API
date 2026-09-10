CREATE TABLE payment_webhook_events (
                                        id UUID PRIMARY KEY DEFAULT gen_random_uuid(),

                                        provider VARCHAR(30) NOT NULL,

                                        event_key VARCHAR(180) NOT NULL,

                                        event_type VARCHAR(60) NOT NULL,

                                        reference VARCHAR(100) NOT NULL,

                                        provider_transaction_id VARCHAR(100) NOT NULL,

                                        payload_hash VARCHAR(64) NOT NULL,

                                        status VARCHAR(20) NOT NULL,

                                        created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

                                        processed_at TIMESTAMP,

                                        CONSTRAINT ux_payment_webhook_events_event_key
                                            UNIQUE (event_key),

                                        CONSTRAINT chk_payment_webhook_events_status
                                            CHECK (
                                                status IN (
                                                           'RECEIVED',
                                                           'PROCESSED'
                                                    )
                                                )
);

CREATE INDEX idx_payment_webhook_events_reference
    ON payment_webhook_events(reference);

CREATE INDEX idx_payment_webhook_events_provider_transaction
    ON payment_webhook_events(provider_transaction_id);

CREATE INDEX idx_payment_webhook_events_created_at
    ON payment_webhook_events(created_at);