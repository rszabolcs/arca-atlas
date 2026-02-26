-- V4: Event records table
CREATE TABLE event_records (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    chain_id BIGINT NOT NULL,
    proxy_address VARCHAR(42) NOT NULL,
    package_key VARCHAR(66) NOT NULL,
    event_type VARCHAR(40) NOT NULL,
    emitting_address VARCHAR(42) NOT NULL,
    block_number BIGINT NOT NULL,
    block_hash VARCHAR(66) NOT NULL,
    tx_hash VARCHAR(66) NOT NULL,
    log_index INT NOT NULL,
    block_timestamp TIMESTAMPTZ NOT NULL,
    raw_data JSONB,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Idempotency guard
CREATE UNIQUE INDEX idx_event_records_tx_log ON event_records(tx_hash, log_index);

-- Query indexes
CREATE INDEX idx_event_records_package ON event_records(chain_id, proxy_address, package_key, block_number);
CREATE INDEX idx_event_records_emitter ON event_records(emitting_address);
CREATE INDEX idx_event_records_timestamp ON event_records(block_timestamp);
CREATE INDEX idx_event_records_type ON event_records(event_type);
