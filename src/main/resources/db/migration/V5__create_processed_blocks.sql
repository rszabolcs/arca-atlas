-- V5: Create processed_blocks table (indexer reorg tracking)
CREATE TABLE processed_blocks (
    id             BIGSERIAL PRIMARY KEY,
    chain_id       BIGINT       NOT NULL,
    proxy_address  VARCHAR(42)  NOT NULL,
    block_number   BIGINT       NOT NULL,
    block_hash     VARCHAR(66)  NOT NULL,

    CONSTRAINT uq_processed_blocks_key UNIQUE (chain_id, proxy_address, block_number)
);

CREATE INDEX idx_processed_blocks_lookup ON processed_blocks (chain_id, proxy_address, block_number DESC);
