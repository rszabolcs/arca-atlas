package com.arcadigitalis.backend.evm;

import com.arcadigitalis.backend.persistence.entity.EventRecordEntity;
import com.arcadigitalis.backend.persistence.repository.EventRecordRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Service layer for querying indexed events.
 * Separates persistence access from the api layer.
 */
@Service
public class EventQueryService {

    private final EventRecordRepository eventRecordRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public EventQueryService(EventRecordRepository eventRecordRepository) {
        this.eventRecordRepository = eventRecordRepository;
    }

    public EventPage query(long chainId, String proxyAddress, String packageKey, int page, int limit) {
        PageRequest pageRequest = PageRequest.of(page, limit,
            Sort.by(Sort.Direction.ASC, "blockNumber", "logIndex"));

        Page<EventRecordEntity> result;
        if (packageKey != null && !packageKey.isBlank()) {
            result = eventRecordRepository.findByChainIdAndProxyAddressAndPackageKeyOrderByBlockNumberAscLogIndexAsc(
                chainId, proxyAddress, packageKey, pageRequest);
        } else {
            result = eventRecordRepository.findByChainIdAndProxyAddressOrderByBlockNumberAscLogIndexAsc(
                chainId, proxyAddress, pageRequest);
        }

        var items = result.getContent().stream().map(this::toRecord).toList();
        String nextCursor = result.hasNext() ? String.valueOf(page + 1) : null;

        return new EventPage(items, result.getTotalElements(), nextCursor);
    }

    private EventRecord toRecord(EventRecordEntity entity) {
        Map<String, Object> data = parseRawData(entity.getRawData());
        return new EventRecord(
            entity.getId().toString(),
            entity.getChainId(),
            entity.getProxyAddress(),
            entity.getPackageKey(),
            entity.getEventType(),
            entity.getEmittingAddress(),
            entity.getBlockNumber(),
            entity.getTxHash(),
            entity.getLogIndex(),
            entity.getBlockTimestamp(),
            data
        );
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseRawData(String rawData) {
        if (rawData == null || rawData.isBlank()) return Collections.emptyMap();
        try {
            return objectMapper.readValue(rawData, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            return Collections.emptyMap();
        }
    }

    public record EventRecord(String id, long chainId, String proxyAddress, String packageKey,
                               String eventType, String emittingAddress, long blockNumber,
                               String txHash, int logIndex, Instant blockTimestamp,
                               Map<String, Object> data) {}

    public record EventPage(List<EventRecord> items, long totalElements, String nextCursor) {}
}
