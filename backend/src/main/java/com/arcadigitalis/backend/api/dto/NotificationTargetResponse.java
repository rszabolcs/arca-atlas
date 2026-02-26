package com.arcadigitalis.backend.api.dto;

import java.time.Instant;
import java.util.UUID;

public record NotificationTargetResponse(
    UUID id,
    String packageKey,
    String[] eventTypes,
    String channelType,
    boolean active,
    Instant createdAt
) {
}
