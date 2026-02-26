package com.arcadigitalis.backend.persistence.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(
    name = "package_cache",
    uniqueConstraints = @UniqueConstraint(columnNames = {"chain_id", "proxy_address", "package_key"})
)
public class PackageCacheEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "chain_id", nullable = false)
    private Long chainId;

    @Column(name = "proxy_address", nullable = false, length = 42)
    private String proxyAddress;

    @Column(name = "package_key", nullable = false, length = 66)
    private String packageKey;

    @Column(name = "owner_address", length = 42)
    private String ownerAddress;

    @Column(name = "beneficiary_address", length = 42)
    private String beneficiaryAddress;

    @Column(name = "manifest_uri", columnDefinition = "TEXT")
    private String manifestUri;

    @Column(name = "cached_status", length = 20)
    private String cachedStatus;

    @Column(name = "pending_since")
    private Instant pendingSince;

    @Column(name = "released_at")
    private Instant releasedAt;

    @Column(name = "last_check_in")
    private Instant lastCheckIn;

    @Column(name = "paid_until")
    private Instant paidUntil;

    @Column(name = "last_indexed_block")
    private Long lastIndexedBlock;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @OneToMany(mappedBy = "packageCache", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<GuardianCacheEntity> guardians = new ArrayList<>();

    // Constructors
    public PackageCacheEntity() {
    }

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
        updatedAt = Instant.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
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

    public String getOwnerAddress() {
        return ownerAddress;
    }

    public void setOwnerAddress(String ownerAddress) {
        this.ownerAddress = ownerAddress;
    }

    public String getBeneficiaryAddress() {
        return beneficiaryAddress;
    }

    public void setBeneficiaryAddress(String beneficiaryAddress) {
        this.beneficiaryAddress = beneficiaryAddress;
    }

    public String getManifestUri() {
        return manifestUri;
    }

    public void setManifestUri(String manifestUri) {
        this.manifestUri = manifestUri;
    }

    public String getCachedStatus() {
        return cachedStatus;
    }

    public void setCachedStatus(String cachedStatus) {
        this.cachedStatus = cachedStatus;
    }

    public Instant getPendingSince() {
        return pendingSince;
    }

    public void setPendingSince(Instant pendingSince) {
        this.pendingSince = pendingSince;
    }

    public Instant getReleasedAt() {
        return releasedAt;
    }

    public void setReleasedAt(Instant releasedAt) {
        this.releasedAt = releasedAt;
    }

    public Instant getLastCheckIn() {
        return lastCheckIn;
    }

    public void setLastCheckIn(Instant lastCheckIn) {
        this.lastCheckIn = lastCheckIn;
    }

    public Instant getPaidUntil() {
        return paidUntil;
    }

    public void setPaidUntil(Instant paidUntil) {
        this.paidUntil = paidUntil;
    }

    public Long getLastIndexedBlock() {
        return lastIndexedBlock;
    }

    public void setLastIndexedBlock(Long lastIndexedBlock) {
        this.lastIndexedBlock = lastIndexedBlock;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    public List<GuardianCacheEntity> getGuardians() {
        return guardians;
    }

    public void setGuardians(List<GuardianCacheEntity> guardians) {
        this.guardians = guardians;
    }
}
