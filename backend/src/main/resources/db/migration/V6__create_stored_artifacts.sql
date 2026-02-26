-- V6: Stored artifacts table
CREATE TABLE stored_artifacts (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    chain_id BIGINT NOT NULL,
    proxy_address VARCHAR(42) NOT NULL,
    package_key VARCHAR(66) NOT NULL,
    artifact_type VARCHAR(20) NOT NULL,
    sha256_hash VARCHAR(64) NOT NULL,
    size_bytes BIGINT NOT NULL,
    ipfs_uri TEXT,
    s3_uri TEXT,
    storage_confirmed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX idx_stored_artifacts_hash ON stored_artifacts(sha256_hash);
CREATE INDEX idx_stored_artifacts_package ON stored_artifacts(chain_id, proxy_address, package_key, artifact_type);
