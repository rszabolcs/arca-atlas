-- V8: Additional performance indexes for 1M+ row queries (NFR-006)

-- Covering index for package cache queries by owner
CREATE INDEX idx_package_cache_owner_covering
    ON package_cache(owner_address, chain_id, proxy_address)
    INCLUDE (package_key, cached_status, updated_at);

-- Covering index for event records pagination
CREATE INDEX idx_event_records_pagination
    ON event_records(package_key, block_number, log_index)
    INCLUDE (event_type, block_timestamp, raw_data);

-- Partial index for active notification targets (already in V7 but documented here for clarity)
-- CREATE INDEX idx_notification_targets_active ON notification_targets(chain_id, proxy_address, package_key) WHERE active = true;
