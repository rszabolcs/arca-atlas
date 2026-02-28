-- V8: Additional composite indexes for query performance (NFR-006: p95 ≤ 500ms at 1M rows)
CREATE INDEX idx_package_cache_owner_chain ON package_cache (owner_address, chain_id, proxy_address);
CREATE INDEX idx_event_records_pkg_block_log ON event_records (package_key, block_number, log_index);
-- partial index on notification_targets already created in V7 (idx_notification_targets_active)
