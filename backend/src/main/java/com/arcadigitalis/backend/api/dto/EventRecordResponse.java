package com.arcadigitalis.backend.api.dto;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record EventRecordResponse(
    UUID id,
    String eventType,
    String packageKey,
    Long blockNumber,
    Instant blockTimestamp,
    String txHash,
    Map<String, Object> rawData
) {
}
