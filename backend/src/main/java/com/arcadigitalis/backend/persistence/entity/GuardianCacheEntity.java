package com.arcadigitalis.backend.persistence.entity;

import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(
    name = "guardian_cache",
    uniqueConstraints = @UniqueConstraint(columnNames = {"package_cache_id", "guardian_address"})
)
public class GuardianCacheEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "package_cache_id", nullable = false)
    private PackageCacheEntity packageCache;

    @Column(name = "guardian_address", nullable = false, length = 42)
    private String guardianAddress;

    @Column(name = "position", nullable = false)
    private Short position;

    // Constructors
    public GuardianCacheEntity() {
    }

    public GuardianCacheEntity(PackageCacheEntity packageCache, String guardianAddress, Short position) {
        this.packageCache = packageCache;
        this.guardianAddress = guardianAddress;
        this.position = position;
    }

    // Getters and Setters
    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public PackageCacheEntity getPackageCache() {
        return packageCache;
    }

    public void setPackageCache(PackageCacheEntity packageCache) {
        this.packageCache = packageCache;
    }

    public String getGuardianAddress() {
        return guardianAddress;
    }

    public void setGuardianAddress(String guardianAddress) {
        this.guardianAddress = guardianAddress;
    }

    public Short getPosition() {
        return position;
    }

    public void setPosition(Short position) {
        this.position = position;
    }
}
