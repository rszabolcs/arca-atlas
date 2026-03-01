package com.arcadigitalis.backend.persistence.repository;

import com.arcadigitalis.backend.persistence.entity.EventRecordEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface EventRecordRepository extends JpaRepository<EventRecordEntity, UUID> {

    boolean existsByTxHashAndLogIndex(String txHash, int logIndex);

    Page<EventRecordEntity> findByChainIdAndProxyAddressOrderByBlockNumberAscLogIndexAsc(
            long chainId, String proxyAddress, Pageable pageable);

    Page<EventRecordEntity> findByChainIdAndProxyAddressAndPackageKeyOrderByBlockNumberAscLogIndexAsc(
            long chainId, String proxyAddress, String packageKey, Pageable pageable);

    @Modifying
    @Query("DELETE FROM EventRecordEntity e WHERE e.chainId = :chainId AND e.proxyAddress = :proxyAddress AND e.blockNumber > :blockNumber")
    int deleteByChainIdAndProxyAddressAndBlockNumberGreaterThan(long chainId, String proxyAddress, long blockNumber);
}
