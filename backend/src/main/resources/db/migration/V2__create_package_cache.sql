-- V2: Package cache table
CREATE TABLE package_cache (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    chain_id BIGINT NOT NULL,
    proxy_address VARCHAR(42) NOT NULL,
    package_key VARCHAR(66) NOT NULL,
    owner_address VARCHAR(42),
    beneficiary_address VARCHAR(42),
    manifest_uri TEXT,
    cached_status VARCHAR(20),
    pending_since TIMESTAMPTZ,
    released_at TIMESTAMPTZ,
    last_check_in TIMESTAMPTZ,
    paid_until TIMESTAMPTZ,
    last_indexed_block BIGINT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX idx_package_cache_key ON package_cache(chain_id, proxy_address, package_key);
CREATE INDEX idx_package_cache_owner ON package_cache(owner_address);
CREATE INDEX idx_package_cache_beneficiary ON package_cache(beneficiary_address);
