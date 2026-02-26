-- V3: Guardian cache table
-- Flyway applies migrations in version order (V3 after V2), so FK to package_cache is safe
CREATE TABLE guardian_cache (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    package_cache_id UUID NOT NULL REFERENCES package_cache(id) ON DELETE CASCADE,
    guardian_address VARCHAR(42) NOT NULL,
    position SMALLINT NOT NULL
);

CREATE UNIQUE INDEX idx_guardian_cache_unique ON guardian_cache(package_cache_id, guardian_address);
