package com.arcadigitalis.backend.persistence.repository;

import com.arcadigitalis.backend.persistence.entity.PackageCacheEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface PackageCacheRepository extends JpaRepository<PackageCacheEntity, UUID> {

    Optional<PackageCacheEntity> findByChainIdAndProxyAddressAndPackageKey(
        Long chainId,
        String proxyAddress,
        String packageKey
    );

    boolean existsByChainIdAndProxyAddressAndPackageKey(
        Long chainId,
        String proxyAddress,
        String packageKey
    );
}
