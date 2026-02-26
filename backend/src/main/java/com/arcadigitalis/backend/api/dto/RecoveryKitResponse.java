package com.arcadigitalis.backend.api.dto;

import com.fasterxml.jackson.databind.node.ObjectNode;

import java.time.Instant;

public record RecoveryKitResponse(
    Long chainId,
    String proxyAddress,
    String packageKey,
    String currentStatus,
    String manifestUri,
    Instant releasedAt,
    ObjectNode accCondition,
    String beneficiaryAddress,
    boolean liveRead
) {
}
