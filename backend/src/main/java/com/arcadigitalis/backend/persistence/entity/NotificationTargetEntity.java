package com.arcadigitalis.backend.persistence.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
    name = "notification_targets",
    uniqueConstraints = @UniqueConstraint(columnNames = {
        "chain_id", "proxy_address", "package_key", "subscriber_address", "channel_type", "channel_value"
    })
)
public class NotificationTargetEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "chain_id", nullable = false)
    private Long chainId;

    @Column(name = "proxy_address", nullable = false, length = 42)
    private String proxyAddress;

    @Column(name = "package_key", nullable = false, length = 66)
    private String packageKey;

    @Column(name = "subscriber_address", nullable = false, length = 42)
    private String subscriberAddress;

    @Column(name = "event_types", nullable = false, columnDefinition = "varchar(40)[]")
    private String[] eventTypes;

    @Column(name = "channel_type", nullable = false, length = 20)
    private String channelType;

    @Column(name = "channel_value", nullable = false, columnDefinition = "TEXT")
    private String channelValue;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    @Column(name = "last_delivery_status", length = 20)
    private String lastDeliveryStatus;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    // Constructors
    public NotificationTargetEntity() {
    }

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
    }

    // Getters and Setters
    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public Long getChainId() {
        return chainId;
    }

    public void setChainId(Long chainId) {
        this.chainId = chainId;
    }

    public String getProxyAddress() {
        return proxyAddress;
    }

    public void setProxyAddress(String proxyAddress) {
        this.proxyAddress = proxyAddress;
    }

    public String getPackageKey() {
        return packageKey;
    }

    public void setPackageKey(String packageKey) {
        this.packageKey = packageKey;
    }

    public String getSubscriberAddress() {
        return subscriberAddress;
    }

    public void setSubscriberAddress(String subscriberAddress) {
        this.subscriberAddress = subscriberAddress;
    }

    public String[] getEventTypes() {
        return eventTypes;
    }

    public void setEventTypes(String[] eventTypes) {
        this.eventTypes = eventTypes;
    }

    public String getChannelType() {
        return channelType;
    }

    public void setChannelType(String channelType) {
        this.channelType = channelType;
    }

    public String getChannelValue() {
        return channelValue;
    }

    public void setChannelValue(String channelValue) {
        this.channelValue = channelValue;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public String getLastDeliveryStatus() {
        return lastDeliveryStatus;
    }

    public void setLastDeliveryStatus(String lastDeliveryStatus) {
        this.lastDeliveryStatus = lastDeliveryStatus;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
