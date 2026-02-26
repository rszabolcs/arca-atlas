package com.arcadigitalis.backend.evm;

import com.arcadigitalis.backend.persistence.entity.EventRecordEntity;
import com.arcadigitalis.backend.persistence.entity.ProcessedBlockEntity;
import com.arcadigitalis.backend.persistence.repository.EventRecordRepository;
import com.arcadigitalis.backend.persistence.repository.ProcessedBlockRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameter;
import org.web3j.protocol.core.methods.request.EthFilter;
import org.web3j.protocol.core.methods.response.EthBlock;
import org.web3j.protocol.core.methods.response.EthLog;
import org.web3j.protocol.core.methods.response.Log;

import java.math.BigInteger;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class IndexerPoller {

    private final Web3j web3j;
    private final EventDecoder eventDecoder;
    private final ReorgHandler reorgHandler;
    private final ProcessedBlockRepository processedBlockRepository;
    private final EventRecordRepository eventRecordRepository;
    private final ApplicationEventPublisher eventPublisher;

    @Value("${arca.evm.chain-id}")
    private Long chainId;

    @Value("${arca.evm.policy-proxy-address}")
    private String proxyAddress;

    @Value("${arca.indexer.enabled:true}")
    private boolean enabled;

    @Value("${arca.indexer.start-block}")
    private Long startBlock;

    @Value("${arca.indexer.confirmation-depth:12}")
    private int confirmationDepth;

    @Value("${arca.indexer.poll-interval-seconds:15}")
    private int pollIntervalSeconds;

    private Long lastProcessedBlock = null;

    public IndexerPoller(Web3j web3j, EventDecoder eventDecoder, ReorgHandler reorgHandler,
                          ProcessedBlockRepository processedBlockRepository,
                          EventRecordRepository eventRecordRepository,
                          ApplicationEventPublisher eventPublisher) {
        this.web3j = web3j;
        this.eventDecoder = eventDecoder;
        this.reorgHandler = reorgHandler;
        this.processedBlockRepository = processedBlockRepository;
        this.eventRecordRepository = eventRecordRepository;
        this.eventPublisher = eventPublisher;
    }

    @Scheduled(fixedDelayString = "${arca.indexer.poll-interval-seconds:15}000")
    @Transactional
    public void poll() {
        if (!enabled) {
            return;
        }

        try {
            // Get current block
            BigInteger currentBlock = web3j.ethBlockNumber().send().getBlockNumber();
            BigInteger confirmedBlock = currentBlock.subtract(BigInteger.valueOf(confirmationDepth));

            // Determine starting point
            Long fromBlock;
            if (lastProcessedBlock == null) {
                // First run - check DB for last processed block
                Optional<Long> maxBlock = processedBlockRepository.findMaxBlockNumber(chainId, proxyAddress);
                fromBlock = maxBlock.orElse(startBlock);
                lastProcessedBlock = fromBlock;
            } else {
                fromBlock = lastProcessedBlock + 1;
            }

            Long toBlock = confirmedBlock.longValue();

            if (fromBlock > toBlock) {
                return; // Nothing to process yet
            }

            // Fetch logs
            EthFilter filter = new EthFilter(
                DefaultBlockParameter.valueOf(BigInteger.valueOf(fromBlock)),
                DefaultBlockParameter.valueOf(BigInteger.valueOf(toBlock)),
                proxyAddress
            );

            EthLog ethLog = web3j.ethGetLogs(filter).send();
            List<EthLog.LogResult> logs = ethLog.getLogs();

            // Process each log
            for (EthLog.LogResult logResult : logs) {
                Log log = (Log) logResult.get();

                // Check for reorg
                Long reorgPoint = reorgHandler.checkAndHandleReorg(
                    chainId,
                    proxyAddress,
                    log.getBlockNumber().longValue(),
                    log.getBlockHash()
                );

                if (reorgPoint != null) {
                    // Reorg detected, restart from fork point
                    lastProcessedBlock = reorgPoint;
                    return;
                }

                // Decode event
                EventDecoder.DecodedEvent decodedEvent = eventDecoder.decode(log);

                // Check idempotency
                if (eventRecordRepository.existsByTxHashAndLogIndex(
                    log.getTransactionHash(), log.getLogIndex().intValue())) {
                    continue; // Already processed
                }

                // Persist event
                EventRecordEntity entity = new EventRecordEntity();
                entity.setChainId(chainId);
                entity.setProxyAddress(proxyAddress);
                entity.setPackageKey(decodedEvent.packageKey());
                entity.setEventType(decodedEvent.eventType());
                entity.setEmittingAddress(log.getAddress());
                entity.setBlockNumber(log.getBlockNumber().longValue());
                entity.setBlockHash(log.getBlockHash());
                entity.setTxHash(log.getTransactionHash());
                entity.setLogIndex(log.getLogIndex().intValue());
                entity.setBlockTimestamp(getBlockTimestamp(log.getBlockNumber().longValue()));
                entity.setRawData(new HashMap<>(decodedEvent.data()));

                eventRecordRepository.save(entity);

                // Publish application event for notification dispatcher
                eventPublisher.publishEvent(new IndexedEventNotification(entity));

                // Update processed blocks
                ProcessedBlockEntity processedBlock = new ProcessedBlockEntity(
                    chainId,
                    proxyAddress,
                    log.getBlockNumber().longValue(),
                    log.getBlockHash()
                );
                processedBlockRepository.save(processedBlock);

                lastProcessedBlock = log.getBlockNumber().longValue();
            }

        } catch (Exception e) {
            System.err.println("Indexer poll failed: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private Instant getBlockTimestamp(Long blockNumber) {
        try {
            EthBlock block = web3j.ethGetBlockByNumber(
                DefaultBlockParameter.valueOf(BigInteger.valueOf(blockNumber)),
                false
            ).send();

            return Instant.ofEpochSecond(block.getBlock().getTimestamp().longValue());
        } catch (Exception e) {
            return Instant.now();
        }
    }

    public record IndexedEventNotification(EventRecordEntity event) {}
}
