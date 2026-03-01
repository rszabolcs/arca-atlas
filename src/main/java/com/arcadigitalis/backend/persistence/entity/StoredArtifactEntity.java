package com.arcadigitalis.backend.persistence.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "stored_artifacts", uniqueConstraints = {
    @UniqueConstraint(name = "uq_stored_artifacts_hash", columnNames = {"sha256_hash"})
})
public class StoredArtifactEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "chain_id")
    private Long chainId;

    @Column(name = "proxy_address", length = 42)
    private String proxyAddress;

    @Column(name = "package_key", length = 66)
    private String packageKey;

    @Column(name = "artifact_type", nullable = false, length = 20)
    private String artifactType;

    @Column(name = "sha256_hash", nullable = false, length = 66)
    private String sha256Hash;

    @Column(name = "ipfs_uri", columnDefinition = "TEXT")
    private String ipfsUri;

    @Column(name = "s3_uri", columnDefinition = "TEXT")
    private String s3Uri;

    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "storage_confirmed_at")
    private Instant storageConfirmedAt;

    protected StoredArtifactEntity() {}

    public StoredArtifactEntity(String artifactType, String sha256Hash, long sizeBytes) {
        this.artifactType = artifactType;
        this.sha256Hash = sha256Hash;
        this.sizeBytes = sizeBytes;
        this.createdAt = Instant.now();
    }

    public UUID getId() { return id; }
    public Long getChainId() { return chainId; }
    public String getProxyAddress() { return proxyAddress; }
    public String getPackageKey() { return packageKey; }
    public String getArtifactType() { return artifactType; }
    public String getSha256Hash() { return sha256Hash; }
    public String getIpfsUri() { return ipfsUri; }
    public String getS3Uri() { return s3Uri; }
    public long getSizeBytes() { return sizeBytes; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getStorageConfirmedAt() { return storageConfirmedAt; }

    public void setChainId(Long chainId) { this.chainId = chainId; }
    public void setProxyAddress(String proxyAddress) { this.proxyAddress = proxyAddress; }
    public void setPackageKey(String packageKey) { this.packageKey = packageKey; }
    public void setIpfsUri(String ipfsUri) { this.ipfsUri = ipfsUri; }
    public void setS3Uri(String s3Uri) { this.s3Uri = s3Uri; }
    public void setStorageConfirmedAt(Instant storageConfirmedAt) { this.storageConfirmedAt = storageConfirmedAt; }
}
