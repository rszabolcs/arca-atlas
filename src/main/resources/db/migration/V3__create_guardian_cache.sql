-- V3: Create guardian_cache table (cached on-chain guardian list per package)
CREATE TABLE guardian_cache (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    package_cache_id  UUID         NOT NULL REFERENCES package_cache(id) ON DELETE CASCADE,
    guardian_address   VARCHAR(42)  NOT NULL,
    position          SMALLINT     NOT NULL,

    CONSTRAINT uq_guardian_cache_entry UNIQUE (package_cache_id, guardian_address)
);
