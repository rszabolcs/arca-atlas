package com.arcadigitalis.backend.persistence.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "processed_blocks", uniqueConstraints = {
    @UniqueConstraint(name = "uq_processed_blocks_key", columnNames = {"chain_id", "proxy_address", "block_number"})
})
public class ProcessedBlockEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "chain_id", nullable = false)
    private long chainId;

    @Column(name = "proxy_address", nullable = false, length = 42)
    private String proxyAddress;

    @Column(name = "block_number", nullable = false)
    private long blockNumber;

    @Column(name = "block_hash", nullable = false, length = 66)
    private String blockHash;

    protected ProcessedBlockEntity() {}

    public ProcessedBlockEntity(long chainId, String proxyAddress, long blockNumber, String blockHash) {
        this.chainId = chainId;
        this.proxyAddress = proxyAddress;
        this.blockNumber = blockNumber;
        this.blockHash = blockHash;
    }

    public Long getId() { return id; }
    public long getChainId() { return chainId; }
    public String getProxyAddress() { return proxyAddress; }
    public long getBlockNumber() { return blockNumber; }
    public String getBlockHash() { return blockHash; }
}
