package com.arcadigitalis.backend.api.dto;

import java.util.List;

/**
 * Request body for POST /notifications/subscriptions.
 */
public record NotificationTargetRequest(
    long chainId,
    String proxyAddress,
    String packageKey,
    List<String> eventTypes,
    String channelType,
    String channelValue
) {}
