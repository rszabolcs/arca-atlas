package com.arcadigitalis.backend.api.dto;

import java.time.Instant;
import java.util.Map;

/**
 * Response DTO for a single event record.
 */
public record EventRecordResponse(
    String id,
    long chainId,
    String proxyAddress,
    String packageKey,
    String eventType,
    String emittingAddress,
    long blockNumber,
    String txHash,
    int logIndex,
    Instant blockTimestamp,
    Map<String, Object> data
) {}
