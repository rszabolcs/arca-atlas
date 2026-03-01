package com.arcadigitalis.backend.evm;

import com.arcadigitalis.backend.evm.EventDecoder.DecodedEvent;
import com.arcadigitalis.backend.evm.EventDecoder.UnknownEventException;
import com.arcadigitalis.backend.persistence.entity.EventRecordEntity;
import com.arcadigitalis.backend.persistence.entity.PackageCacheEntity;
import com.arcadigitalis.backend.persistence.entity.ProcessedBlockEntity;
import com.arcadigitalis.backend.persistence.repository.EventRecordRepository;
import com.arcadigitalis.backend.persistence.repository.PackageCacheRepository;
import com.arcadigitalis.backend.persistence.repository.ProcessedBlockRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.web3j.protocol.Web3j;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import org.web3j.protocol.core.DefaultBlockParameterNumber;
import org.web3j.protocol.core.methods.request.EthFilter;
import org.web3j.protocol.core.methods.response.EthBlock;
import org.web3j.protocol.core.methods.response.EthLog;
import org.web3j.protocol.core.methods.response.Log;

import java.math.BigInteger;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Background indexer that polls for new blocks, decodes contract events,
 * updates the package cache, and persists events idempotently.
 * Single thread — no concurrent indexer instances (NFR-002).
 */
@Component
public class IndexerPoller {

    private static final Logger log = LoggerFactory.getLogger(IndexerPoller.class);

    private final Web3j web3j;
    private final Web3jConfig config;
    private final EventDecoder eventDecoder;
    private final ReorgHandler reorgHandler;
    private final EventRecordRepository eventRecordRepository;
    private final ProcessedBlockRepository processedBlockRepository;
    private final PackageCacheRepository packageCacheRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final ObjectMapper objectMapper;
    private final DataSource dataSource;

    @Value("${arca.indexer.enabled:true}")
    private boolean enabled;

    @Value("${arca.indexer.start-block:0}")
    private long startBlock;

    @Value("${arca.indexer.confirmation-depth:12}")
    private int confirmationDepth;

    @Value("${arca.indexer.lock-id:0}")
    private long configuredLockId;

    private final AtomicLong lastSyncTimestamp = new AtomicLong(0);
    private boolean initialized = false;
    private boolean lockAcquired = false;

    public IndexerPoller(Web3j web3j, Web3jConfig config, EventDecoder eventDecoder,
                         ReorgHandler reorgHandler, EventRecordRepository eventRecordRepository,
                         ProcessedBlockRepository processedBlockRepository,
                         PackageCacheRepository packageCacheRepository,
                         ApplicationEventPublisher eventPublisher,
                         DataSource dataSource) {
        this.web3j = web3j;
        this.config = config;
        this.eventDecoder = eventDecoder;
        this.reorgHandler = reorgHandler;
        this.eventRecordRepository = eventRecordRepository;
        this.processedBlockRepository = processedBlockRepository;
        this.packageCacheRepository = packageCacheRepository;
        this.eventPublisher = eventPublisher;
        this.objectMapper = new ObjectMapper();
        this.dataSource = dataSource;
    }

    @PostConstruct
    public void init() {
        if (!enabled) {
            log.info("Indexer is disabled (arca.indexer.enabled=false)");
            return;
        }
        // Attempt to acquire PostgreSQL advisory lock (NFR-002)
        long lockId = configuredLockId != 0 ? configuredLockId : deriveLockId(config.getProxyAddress());
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT pg_try_advisory_lock(?)")) {
            ps.setLong(1, lockId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next() && rs.getBoolean(1)) {
                    lockAcquired = true;
                    log.info("Indexer advisory lock {} acquired — this instance will run the indexer", lockId);
                } else {
                    lockAcquired = false;
                    log.warn("Indexer advisory lock {} held by another instance — skipping indexer startup", lockId);
                }
            }
        } catch (Exception e) {
            // If we can't connect to DB yet, allow the poll loop to try later
            log.warn("Could not acquire advisory lock at startup: {}. Indexer will attempt polling anyway.", e.getMessage());
            lockAcquired = true;
        }
    }

    /**
     * Derives a deterministic lock ID from the proxy address hash.
     */
    static long deriveLockId(String proxyAddress) {
        if (proxyAddress == null) return 1;
        return Math.abs(proxyAddress.toLowerCase().hashCode());
    }

    public long getLastSyncTimestamp() {
        return lastSyncTimestamp.get();
    }

    @Scheduled(fixedDelayString = "${arca.indexer.poll-interval-seconds:15}000")
    public void poll() {
        if (!enabled || !lockAcquired) return;

        try {
            long latestBlock = web3j.ethBlockNumber().send().getBlockNumber().longValue();
            long confirmedBlock = latestBlock - confirmationDepth;
            if (confirmedBlock < 0) return;

            long fromBlock = determineFromBlock();
            if (fromBlock > confirmedBlock) {
                return; // Nothing new to process
            }

            log.debug("Indexing blocks {} to {} (latest={}, depth={})",
                fromBlock, confirmedBlock, latestBlock, confirmationDepth);

            // Fetch logs from the proxy contract in the block range
            EthFilter filter = new EthFilter(
                new DefaultBlockParameterNumber(fromBlock),
                new DefaultBlockParameterNumber(confirmedBlock),
                config.getProxyAddress()
            );

            EthLog ethLog = web3j.ethGetLogs(filter).send();
            if (ethLog.hasError()) {
                log.warn("eth_getLogs error: {}", ethLog.getError().getMessage());
                return;
            }

            List<EthLog.LogResult> logResults = ethLog.getLogs();
            for (EthLog.LogResult<?> result : logResults) {
                if (result instanceof EthLog.LogObject logObj) {
                    Log logEntry = logObj.get();
                    processLogEntry(logEntry);
                }
            }

            // Mark all blocks in range as processed
            for (long block = fromBlock; block <= confirmedBlock; block++) {
                markBlockProcessed(block);
            }

            lastSyncTimestamp.set(System.currentTimeMillis());
            initialized = true;

        } catch (Exception e) {
            log.error("Indexer polling cycle failed: {}", e.getMessage(), e);
        }
    }

    private long determineFromBlock() {
        if (!initialized) {
            // Check DB for last processed block
            Optional<ProcessedBlockEntity> latest = processedBlockRepository
                .findLatestByChainIdAndProxyAddress(config.getChainId(), config.getProxyAddress());
            if (latest.isPresent()) {
                // Resume from next block after last processed (FR-028a)
                return latest.get().getBlockNumber() + 1;
            }
            // First run — start from configured start block
            return startBlock;
        }
        // Subsequent runs — resume from DB
        Optional<ProcessedBlockEntity> latest = processedBlockRepository
            .findLatestByChainIdAndProxyAddress(config.getChainId(), config.getProxyAddress());
        return latest.map(e -> e.getBlockNumber() + 1).orElse(startBlock);
    }

    private void processLogEntry(Log logEntry) {
        try {
            DecodedEvent event = eventDecoder.decode(logEntry);

            // Check for reorg
            reorgHandler.checkAndHandleReorg(
                config.getChainId(), config.getProxyAddress(),
                event.blockNumber(), event.blockHash()
            );

            // Idempotency check — skip if already stored
            if (eventRecordRepository.existsByTxHashAndLogIndex(event.txHash(), event.logIndex())) {
                log.debug("Skipping duplicate event txHash={} logIndex={}", event.txHash(), event.logIndex());
                return;
            }

            // Persist event record
            EventRecordEntity entity = new EventRecordEntity(
                config.getChainId(),
                config.getProxyAddress(),
                event.packageKey(),
                event.eventType(),
                logEntry.getAddress(),
                event.blockNumber(),
                event.blockHash(),
                event.txHash(),
                event.logIndex(),
                getBlockTimestamp(event.blockNumber()),
                serializeRawData(event.rawData())
            );
            eventRecordRepository.save(entity);

            // Update package cache
            updatePackageCache(event);

            // Publish application event for notification dispatch
            eventPublisher.publishEvent(new IndexedEventNotification(event));

            log.debug("Indexed event: type={} packageKey={} block={}",
                event.eventType(), event.packageKey(), event.blockNumber());

        } catch (UnknownEventException e) {
            log.debug("Skipping unknown event: {}", e.getMessage());
        } catch (Exception e) {
            log.warn("Failed to process log entry: {}", e.getMessage(), e);
        }
    }

    private void updatePackageCache(DecodedEvent event) {
        Optional<PackageCacheEntity> cached = packageCacheRepository
            .findByChainIdAndProxyAddressAndPackageKey(
                config.getChainId(), config.getProxyAddress(), event.packageKey());

        PackageCacheEntity entity = cached.orElseGet(() -> {
            PackageCacheEntity e = new PackageCacheEntity(
                config.getChainId(), config.getProxyAddress(), event.packageKey()
            );
            return e;
        });

        switch (event.eventType()) {
            case "PackageActivated" -> {
                entity.setCachedStatus("ACTIVE");
                if (event.rawData().containsKey("owner"))
                    entity.setOwnerAddress((String) event.rawData().get("owner"));
                if (event.rawData().containsKey("beneficiary"))
                    entity.setBeneficiaryAddress((String) event.rawData().get("beneficiary"));
                if (event.rawData().containsKey("manifestUri"))
                    entity.setManifestUri((String) event.rawData().get("manifestUri"));
            }
            case "ManifestUpdated" -> {
                if (event.rawData().containsKey("manifestUri"))
                    entity.setManifestUri((String) event.rawData().get("manifestUri"));
            }
            case "CheckIn" -> entity.setLastCheckIn(Instant.now());
            case "Renewed" -> {
                if (event.rawData().containsKey("paidUntil")) {
                    long ts = ((Number) event.rawData().get("paidUntil")).longValue();
                    if (ts > 0) entity.setPaidUntil(Instant.ofEpochSecond(ts));
                }
            }
            case "PendingRelease" -> {
                entity.setCachedStatus("PENDING_RELEASE");
                entity.setPendingSince(Instant.now());
            }
            case "Released" -> {
                entity.setCachedStatus("RELEASED");
                entity.setReleasedAt(Instant.now());
            }
            case "Revoked" -> entity.setCachedStatus("REVOKED");
            case "PackageRescued" -> {
                entity.setCachedStatus("ACTIVE");
                entity.setPendingSince(null);
            }
            default -> {} // Guardian events don't update cache status
        }

        entity.setLastIndexedBlock(event.blockNumber());
        entity.touch();
        packageCacheRepository.save(entity);
    }

    private void markBlockProcessed(long blockNumber) {
        if (processedBlockRepository.findByChainIdAndProxyAddressAndBlockNumber(
                config.getChainId(), config.getProxyAddress(), blockNumber).isPresent()) {
            return; // Already processed
        }

        ProcessedBlockEntity block = new ProcessedBlockEntity(
            config.getChainId(), config.getProxyAddress(), blockNumber, getBlockHash(blockNumber)
        );
        processedBlockRepository.save(block);
    }

    private Instant getBlockTimestamp(long blockNumber) {
        try {
            EthBlock block = web3j.ethGetBlockByNumber(
                new DefaultBlockParameterNumber(blockNumber), false).send();
            if (block.getBlock() != null && block.getBlock().getTimestamp() != null) {
                return Instant.ofEpochSecond(block.getBlock().getTimestamp().longValue());
            }
        } catch (Exception e) {
            log.debug("Failed to get block timestamp for block {}: {}", blockNumber, e.getMessage());
        }
        return Instant.now();
    }

    private String getBlockHash(long blockNumber) {
        try {
            EthBlock block = web3j.ethGetBlockByNumber(
                new DefaultBlockParameterNumber(blockNumber), false).send();
            if (block.getBlock() != null) {
                return block.getBlock().getHash();
            }
        } catch (Exception e) {
            log.debug("Failed to get block hash for block {}: {}", blockNumber, e.getMessage());
        }
        return "0x" + "0".repeat(64);
    }

    private String serializeRawData(Object rawData) {
        try {
            return objectMapper.writeValueAsString(rawData);
        } catch (JsonProcessingException e) {
            return "{}";
        }
    }

    // ── Spring application event for notification dispatch ─────────────────

    public record IndexedEventNotification(DecodedEvent event) {}
}
