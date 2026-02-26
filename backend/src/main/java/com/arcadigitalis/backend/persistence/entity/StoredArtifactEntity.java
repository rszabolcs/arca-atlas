package com.arcadigitalis.backend.persistence.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
    name = "stored_artifacts",
    uniqueConstraints = @UniqueConstraint(columnNames = {"sha256_hash"})
)
public class StoredArtifactEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "chain_id", nullable = false)
    private Long chainId;

    @Column(name = "proxy_address", nullable = false, length = 42)
    private String proxyAddress;

    @Column(name = "package_key", nullable = false, length = 66)
    private String packageKey;

    @Column(name = "artifact_type", nullable = false, length = 20)
    private String artifactType;

    @Column(name = "sha256_hash", nullable = false, length = 64)
    private String sha256Hash;

    @Column(name = "size_bytes", nullable = false)
    private Long sizeBytes;

    @Column(name = "ipfs_uri", columnDefinition = "TEXT")
    private String ipfsUri;

    @Column(name = "s3_uri", columnDefinition = "TEXT")
    private String s3Uri;

    @Column(name = "storage_confirmed_at")
    private Instant storageConfirmedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    // Constructors
    public StoredArtifactEntity() {
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

    public String getArtifactType() {
        return artifactType;
    }

    public void setArtifactType(String artifactType) {
        this.artifactType = artifactType;
    }

    public String getSha256Hash() {
        return sha256Hash;
    }

    public void setSha256Hash(String sha256Hash) {
        this.sha256Hash = sha256Hash;
    }

    public Long getSizeBytes() {
        return sizeBytes;
    }

    public void setSizeBytes(Long sizeBytes) {
        this.sizeBytes = sizeBytes;
    }

    public String getIpfsUri() {
        return ipfsUri;
    }

    public void setIpfsUri(String ipfsUri) {
        this.ipfsUri = ipfsUri;
    }

    public String getS3Uri() {
        return s3Uri;
    }

    public void setS3Uri(String s3Uri) {
        this.s3Uri = s3Uri;
    }

    public Instant getStorageConfirmedAt() {
        return storageConfirmedAt;
    }

    public void setStorageConfirmedAt(Instant storageConfirmedAt) {
        this.storageConfirmedAt = storageConfirmedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
