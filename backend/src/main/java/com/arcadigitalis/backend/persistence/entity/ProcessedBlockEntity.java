package com.arcadigitalis.backend.persistence.entity;

import jakarta.persistence.*;

@Entity
@Table(
    name = "processed_blocks",
    uniqueConstraints = @UniqueConstraint(columnNames = {"chain_id", "proxy_address", "block_number"})
)
public class ProcessedBlockEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "chain_id", nullable = false)
    private Long chainId;

    @Column(name = "proxy_address", nullable = false, length = 42)
    private String proxyAddress;

    @Column(name = "block_number", nullable = false)
    private Long blockNumber;

    @Column(name = "block_hash", nullable = false, length = 66)
    private String blockHash;

    // Constructors
    public ProcessedBlockEntity() {
    }

    public ProcessedBlockEntity(Long chainId, String proxyAddress, Long blockNumber, String blockHash) {
        this.chainId = chainId;
        this.proxyAddress = proxyAddress;
        this.blockNumber = blockNumber;
        this.blockHash = blockHash;
    }

    // Getters and Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
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
}
