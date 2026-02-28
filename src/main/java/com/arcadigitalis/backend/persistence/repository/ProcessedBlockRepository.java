package com.arcadigitalis.backend.persistence.repository;

import com.arcadigitalis.backend.persistence.entity.ProcessedBlockEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ProcessedBlockRepository extends JpaRepository<ProcessedBlockEntity, Long> {

    Optional<ProcessedBlockEntity> findByChainIdAndProxyAddressAndBlockNumber(
            long chainId, String proxyAddress, long blockNumber);

    @Query("SELECT p FROM ProcessedBlockEntity p WHERE p.chainId = :chainId AND p.proxyAddress = :proxyAddress ORDER BY p.blockNumber DESC LIMIT 1")
    Optional<ProcessedBlockEntity> findLatestByChainIdAndProxyAddress(long chainId, String proxyAddress);

    @Modifying
    @Query("DELETE FROM ProcessedBlockEntity p WHERE p.chainId = :chainId AND p.proxyAddress = :proxyAddress AND p.blockNumber > :blockNumber")
    int deleteByChainIdAndProxyAddressAndBlockNumberGreaterThan(long chainId, String proxyAddress, long blockNumber);
}
