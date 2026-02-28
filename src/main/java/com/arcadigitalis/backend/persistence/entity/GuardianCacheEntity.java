package com.arcadigitalis.backend.persistence.entity;

import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "guardian_cache", uniqueConstraints = {
    @UniqueConstraint(name = "uq_guardian_cache_entry", columnNames = {"package_cache_id", "guardian_address"})
})
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
    private short position;

    protected GuardianCacheEntity() {}

    public GuardianCacheEntity(PackageCacheEntity packageCache, String guardianAddress, short position) {
        this.packageCache = packageCache;
        this.guardianAddress = guardianAddress;
        this.position = position;
    }

    public UUID getId() { return id; }
    public PackageCacheEntity getPackageCache() { return packageCache; }
    public String getGuardianAddress() { return guardianAddress; }
    public short getPosition() { return position; }
}
