package com.arcadigitalis.backend.api.dto;

import java.util.List;

/**
 * Request body for PUT /notifications/subscriptions/{id}.
 */
public record NotificationTargetUpdateRequest(
    List<String> eventTypes,
    String channelValue,
    Boolean active
) {}
