package com.arcadigitalis.backend.persistence.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "notification_targets", uniqueConstraints = {
    @UniqueConstraint(name = "uq_notification_targets_sub",
        columnNames = {"chain_id", "proxy_address", "package_key", "subscriber_address", "channel_type", "channel_value"})
})
public class NotificationTargetEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "chain_id", nullable = false)
    private long chainId;

    @Column(name = "proxy_address", nullable = false, length = 42)
    private String proxyAddress;

    @Column(name = "package_key", nullable = false, length = 66)
    private String packageKey;

    @Column(name = "subscriber_address", nullable = false, length = 42)
    private String subscriberAddress;

    @Column(name = "event_types", nullable = false, columnDefinition = "VARCHAR(40)[]")
    private String[] eventTypes;

    @Column(name = "channel_type", nullable = false, length = 20)
    private String channelType;

    @Column(name = "channel_value", nullable = false, columnDefinition = "TEXT")
    private String channelValue;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "last_delivery_attempt")
    private Instant lastDeliveryAttempt;

    @Column(name = "last_delivery_status", length = 20)
    private String lastDeliveryStatus;

    protected NotificationTargetEntity() {}

    public NotificationTargetEntity(long chainId, String proxyAddress, String packageKey,
                                    String subscriberAddress, String[] eventTypes,
                                    String channelType, String channelValue) {
        this.chainId = chainId;
        this.proxyAddress = proxyAddress;
        this.packageKey = packageKey;
        this.subscriberAddress = subscriberAddress;
        this.eventTypes = eventTypes;
        this.channelType = channelType;
        this.channelValue = channelValue;
        this.createdAt = Instant.now();
    }

    public UUID getId() { return id; }
    public long getChainId() { return chainId; }
    public String getProxyAddress() { return proxyAddress; }
    public String getPackageKey() { return packageKey; }
    public String getSubscriberAddress() { return subscriberAddress; }
    public String[] getEventTypes() { return eventTypes; }
    public String getChannelType() { return channelType; }
    public String getChannelValue() { return channelValue; }
    public boolean isActive() { return active; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getLastDeliveryAttempt() { return lastDeliveryAttempt; }
    public String getLastDeliveryStatus() { return lastDeliveryStatus; }

    public void setEventTypes(String[] eventTypes) { this.eventTypes = eventTypes; }
    public void setChannelValue(String channelValue) { this.channelValue = channelValue; }
    public void setActive(boolean active) { this.active = active; }
    public void setLastDeliveryAttempt(Instant lastDeliveryAttempt) { this.lastDeliveryAttempt = lastDeliveryAttempt; }
    public void setLastDeliveryStatus(String lastDeliveryStatus) { this.lastDeliveryStatus = lastDeliveryStatus; }
}
