package com.arcadigitalis.backend.persistence.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(
    name = "event_records",
    uniqueConstraints = @UniqueConstraint(columnNames = {"tx_hash", "log_index"})
)
public class EventRecordEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "chain_id", nullable = false)
    private Long chainId;

    @Column(name = "proxy_address", nullable = false, length = 42)
    private String proxyAddress;

    @Column(name = "package_key", nullable = false, length = 66)
    private String packageKey;

    @Column(name = "event_type", nullable = false, length = 40)
    private String eventType;

    @Column(name = "emitting_address", nullable = false, length = 42)
    private String emittingAddress;

    @Column(name = "block_number", nullable = false)
    private Long blockNumber;

    @Column(name = "block_hash", nullable = false, length = 66)
    private String blockHash;

    @Column(name = "tx_hash", nullable = false, length = 66)
    private String txHash;

    @Column(name = "log_index", nullable = false)
    private Integer logIndex;

    @Column(name = "block_timestamp", nullable = false)
    private Instant blockTimestamp;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "raw_data", columnDefinition = "jsonb")
    private Map<String, Object> rawData;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    // Constructors
    public EventRecordEntity() {
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

    public String getEventType() {
        return eventType;
    }

    public void setEventType(String eventType) {
        this.eventType = eventType;
    }

    public String getEmittingAddress() {
        return emittingAddress;
    }

    public void setEmittingAddress(String emittingAddress) {
        this.emittingAddress = emittingAddress;
    }

    public Long getBlockNumber() {
        return blockNumber;
    }

    public void setBlockNumber(Long blockNumber) {
        this.blockNumber = blockNumber;
    }

    public String getBlockHash() {
        return blockHash;
    }

    public void setBlockHash(String blockHash) {
        this.blockHash = blockHash;
    }

    public String getTxHash() {
        return txHash;
    }

    public void setTxHash(String txHash) {
        this.txHash = txHash;
    }

    public Integer getLogIndex() {
        return logIndex;
    }

    public void setLogIndex(Integer logIndex) {
        this.logIndex = logIndex;
    }

    public Instant getBlockTimestamp() {
        return blockTimestamp;
    }

    public void setBlockTimestamp(Instant blockTimestamp) {
        this.blockTimestamp = blockTimestamp;
    }

    public Map<String, Object> getRawData() {
        return rawData;
    }

    public void setRawData(Map<String, Object> rawData) {
        this.rawData = rawData;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
