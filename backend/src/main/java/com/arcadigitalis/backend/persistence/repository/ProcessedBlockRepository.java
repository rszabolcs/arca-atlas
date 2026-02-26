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
        Long chainId,
        String proxyAddress,
        Long blockNumber
    );

    @Query("SELECT MAX(pb.blockNumber) FROM ProcessedBlockEntity pb WHERE pb.chainId = :chainId AND pb.proxyAddress = :proxyAddress")
    Optional<Long> findMaxBlockNumber(Long chainId, String proxyAddress);

    @Modifying
    @Query("DELETE FROM ProcessedBlockEntity pb WHERE pb.chainId = :chainId AND pb.proxyAddress = :proxyAddress AND pb.blockNumber > :blockNumber")
    void deleteByChainIdAndProxyAddressAndBlockNumberGreaterThan(
        Long chainId,
        String proxyAddress,
        Long blockNumber
    );
}
