-- V7: Create notification_targets table (delivery subscriptions)
-- Supported channel_type values: 'email', 'webhook', 'push'
CREATE TABLE notification_targets (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    chain_id                BIGINT       NOT NULL,
    proxy_address           VARCHAR(42)  NOT NULL,
    package_key             VARCHAR(66)  NOT NULL,
    subscriber_address      VARCHAR(42)  NOT NULL,
    event_types             VARCHAR(40)[] NOT NULL,
    channel_type            VARCHAR(20)  NOT NULL,
    channel_value           TEXT         NOT NULL,
    active                  BOOLEAN      NOT NULL DEFAULT true,
    created_at              TIMESTAMPTZ  NOT NULL DEFAULT now(),
    last_delivery_attempt   TIMESTAMPTZ,
    last_delivery_status    VARCHAR(20),

    CONSTRAINT uq_notification_targets_sub UNIQUE (chain_id, proxy_address, package_key, subscriber_address, channel_type, channel_value)
);

CREATE INDEX idx_notification_targets_active ON notification_targets (chain_id, proxy_address, package_key) WHERE active = true;
