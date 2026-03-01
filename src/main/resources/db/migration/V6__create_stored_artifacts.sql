-- V6: Create stored_artifacts table (pinned manifest + ciphertext blobs)
CREATE TABLE stored_artifacts (
    id                    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    chain_id              BIGINT,
    proxy_address         VARCHAR(42),
    package_key           VARCHAR(66),
    artifact_type         VARCHAR(20)  NOT NULL,
    sha256_hash           VARCHAR(66)  NOT NULL,
    ipfs_uri              TEXT,
    s3_uri                TEXT,
    size_bytes            BIGINT       NOT NULL,
    created_at            TIMESTAMPTZ  NOT NULL DEFAULT now(),
    storage_confirmed_at  TIMESTAMPTZ,

    CONSTRAINT uq_stored_artifacts_hash UNIQUE (sha256_hash)
);

CREATE INDEX idx_stored_artifacts_package ON stored_artifacts (chain_id, proxy_address, package_key, artifact_type);
