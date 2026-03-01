package com.arcadigitalis.backend.evm;

import com.arcadigitalis.backend.persistence.entity.EventRecordEntity;
import com.arcadigitalis.backend.persistence.entity.ProcessedBlockEntity;
import com.arcadigitalis.backend.persistence.repository.EventRecordRepository;
import com.arcadigitalis.backend.persistence.repository.ProcessedBlockRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * Detects and handles chain reorganizations by comparing stored block hashes
 * against observed hashes. On mismatch, rewinds the DB to the fork point.
 */
@Component
public class ReorgHandler {

    private static final Logger log = LoggerFactory.getLogger(ReorgHandler.class);

    private final ProcessedBlockRepository processedBlockRepository;
    private final EventRecordRepository eventRecordRepository;

    public ReorgHandler(ProcessedBlockRepository processedBlockRepository,
                        EventRecordRepository eventRecordRepository) {
        this.processedBlockRepository = processedBlockRepository;
        this.eventRecordRepository = eventRecordRepository;
    }

    /**
     * Checks for reorg at the given block number.
     * If the stored block hash does not match the observed hash, rewinds the DB
     * by deleting all processed_blocks and event_records above the fork point.
     *
     * @return the block number to resume from (same as input if no reorg, or fork point if reorg detected)
     */
    @Transactional
    public long checkAndHandleReorg(long chainId, String proxyAddress,
                                     long blockNumber, String observedHash) {
        Optional<ProcessedBlockEntity> stored = processedBlockRepository
            .findByChainIdAndProxyAddressAndBlockNumber(chainId, proxyAddress, blockNumber);

        if (stored.isEmpty()) {
            // Block not yet processed — no reorg to handle
            return blockNumber;
        }

        if (stored.get().getBlockHash().equalsIgnoreCase(observedHash)) {
            // Hash matches — no reorg
            return blockNumber;
        }

        // Reorg detected!
        log.warn("Reorg detected at block {} — stored hash {} vs observed hash {}",
            blockNumber, stored.get().getBlockHash(), observedHash);

        // Walk backwards to find fork point
        long forkPoint = blockNumber;
        while (forkPoint > 0) {
            Optional<ProcessedBlockEntity> prev = processedBlockRepository
                .findByChainIdAndProxyAddressAndBlockNumber(chainId, proxyAddress, forkPoint - 1);
            if (prev.isEmpty()) {
                break; // We haven't processed this block — this is the start
            }
            // We don't have the observed hash for previous blocks without re-querying,
            // so we rewind to the mismatch point
            break;
        }

        // Delete all data from fork point onwards
        log.info("Rewinding event_records and processed_blocks from block {} onwards for chain={} proxy={}",
            forkPoint, chainId, proxyAddress);

        eventRecordRepository.deleteByChainIdAndProxyAddressAndBlockNumberGreaterThan(
            chainId, proxyAddress, forkPoint - 1);
        processedBlockRepository.deleteByChainIdAndProxyAddressAndBlockNumberGreaterThan(
            chainId, proxyAddress, forkPoint - 1);

        return forkPoint;
    }
}
