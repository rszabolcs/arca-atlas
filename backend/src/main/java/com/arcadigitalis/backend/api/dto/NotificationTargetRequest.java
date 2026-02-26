package com.arcadigitalis.backend.api.dto;

public record NotificationTargetRequest(
    String packageKey,
    String[] eventTypes,
    String channelType,
    String channelValue
) {
}
