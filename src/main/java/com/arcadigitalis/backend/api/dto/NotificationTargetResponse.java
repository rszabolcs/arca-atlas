package com.arcadigitalis.backend.api.dto;

import java.time.Instant;
import java.util.List;

/**
 * Response DTO for notification targets.
 */
public record NotificationTargetResponse(
    String id,
    long chainId,
    String proxyAddress,
    String packageKey,
    String subscriberAddress,
    List<String> eventTypes,
    String channelType,
    String channelValue,
    boolean active,
    Instant createdAt,
    Instant lastDeliveryAttempt,
    String lastDeliveryStatus
) {}
