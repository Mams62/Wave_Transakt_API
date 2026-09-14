CREATE TABLE wave_cards (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id),
    wallet_id UUID NOT NULL REFERENCES wallets(id),
    provider VARCHAR(40) NOT NULL,
    provider_card_reference VARCHAR(160) NOT NULL UNIQUE,
    masked_display_reference VARCHAR(80),
    product_code VARCHAR(80),
    status VARCHAR(40) NOT NULL,
    contactless_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

CREATE INDEX idx_wave_cards_user_id ON wave_cards(user_id);
CREATE INDEX idx_wave_cards_wallet_id ON wave_cards(wallet_id);
CREATE INDEX idx_wave_cards_status ON wave_cards(status);
