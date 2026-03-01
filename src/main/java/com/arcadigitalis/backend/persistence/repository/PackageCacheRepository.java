package com.arcadigitalis.backend.persistence.repository;

import com.arcadigitalis.backend.persistence.entity.PackageCacheEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface PackageCacheRepository extends JpaRepository<PackageCacheEntity, UUID> {

    Optional<PackageCacheEntity> findByChainIdAndProxyAddressAndPackageKey(
            long chainId, String proxyAddress, String packageKey);

    Page<PackageCacheEntity> findByOwnerAddress(String ownerAddress, Pageable pageable);

    Page<PackageCacheEntity> findByBeneficiaryAddress(String beneficiaryAddress, Pageable pageable);
}
