package com.arcadigitalis.backend.evm;

import com.arcadigitalis.backend.persistence.entity.ProcessedBlockEntity;
import com.arcadigitalis.backend.persistence.repository.EventRecordRepository;
import com.arcadigitalis.backend.persistence.repository.ProcessedBlockRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
public class ReorgHandler {

    private final ProcessedBlockRepository processedBlockRepository;
    private final EventRecordRepository eventRecordRepository;

    public ReorgHandler(ProcessedBlockRepository processedBlockRepository,
                         EventRecordRepository eventRecordRepository) {
        this.processedBlockRepository = processedBlockRepository;
        this.eventRecordRepository = eventRecordRepository;
    }

    @Transactional
    public Long checkAndHandleReorg(Long chainId, String proxyAddress, Long blockNumber, String observedHash) {
        Optional<ProcessedBlockEntity> stored = processedBlockRepository
            .findByChainIdAndProxyAddressAndBlockNumber(chainId, proxyAddress, blockNumber);

        if (stored.isEmpty()) {
            return null; // No reorg, block not yet processed
        }

        String storedHash = stored.get().getBlockHash();
        if (storedHash.equals(observedHash)) {
            return null; // No reorg, hashes match
        }

        // Reorg detected! Rewind to fork point
        Long forkPoint = blockNumber - 1;

        // Delete all events and processed blocks above the fork point
        eventRecordRepository.deleteByChainIdAndProxyAddressAndBlockNumberGreaterThan(
            chainId, proxyAddress, forkPoint);

        processedBlockRepository.deleteByChainIdAndProxyAddressAndBlockNumberGreaterThan(
            chainId, proxyAddress, forkPoint);

        return forkPoint;
    }
}
