-- V1: SIWE nonce table
CREATE TABLE nonces (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    wallet_address VARCHAR(42) NOT NULL,
    nonce VARCHAR(64) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMPTZ NOT NULL,
    consumed BOOLEAN NOT NULL DEFAULT false
);

CREATE UNIQUE INDEX idx_nonces_nonce ON nonces(nonce);
CREATE INDEX idx_nonces_wallet_unconsumed ON nonces(wallet_address, consumed) WHERE consumed = false;
