package com.arcadigitalis.backend.persistence.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "event_records", uniqueConstraints = {
    @UniqueConstraint(name = "uq_event_records_tx_log", columnNames = {"tx_hash", "log_index"})
})
public class EventRecordEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "chain_id", nullable = false)
    private long chainId;

    @Column(name = "proxy_address", nullable = false, length = 42)
    private String proxyAddress;

    @Column(name = "package_key", nullable = false, length = 66)
    private String packageKey;

    @Column(name = "event_type", nullable = false, length = 40)
    private String eventType;

    @Column(name = "emitting_address", nullable = false, length = 42)
    private String emittingAddress;

    @Column(name = "block_number", nullable = false)
    private long blockNumber;

    @Column(name = "block_hash", nullable = false, length = 66)
    private String blockHash;

    @Column(name = "tx_hash", nullable = false, length = 66)
    private String txHash;

    @Column(name = "log_index", nullable = false)
    private int logIndex;

    @Column(name = "block_timestamp", nullable = false)
    private Instant blockTimestamp;

    @Column(name = "raw_data", columnDefinition = "jsonb")
    private String rawData;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected EventRecordEntity() {}

    public EventRecordEntity(long chainId, String proxyAddress, String packageKey,
                             String eventType, String emittingAddress,
                             long blockNumber, String blockHash,
                             String txHash, int logIndex,
                             Instant blockTimestamp, String rawData) {
        this.chainId = chainId;
        this.proxyAddress = proxyAddress;
        this.packageKey = packageKey;
        this.eventType = eventType;
        this.emittingAddress = emittingAddress;
        this.blockNumber = blockNumber;
        this.blockHash = blockHash;
        this.txHash = txHash;
        this.logIndex = logIndex;
        this.blockTimestamp = blockTimestamp;
        this.rawData = rawData;
        this.createdAt = Instant.now();
    }

    public UUID getId() { return id; }
    public long getChainId() { return chainId; }
    public String getProxyAddress() { return proxyAddress; }
    public String getPackageKey() { return packageKey; }
    public String getEventType() { return eventType; }
    public String getEmittingAddress() { return emittingAddress; }
    public long getBlockNumber() { return blockNumber; }
    public String getBlockHash() { return blockHash; }
    public String getTxHash() { return txHash; }
    public int getLogIndex() { return logIndex; }
    public Instant getBlockTimestamp() { return blockTimestamp; }
    public String getRawData() { return rawData; }
    public Instant getCreatedAt() { return createdAt; }
}
