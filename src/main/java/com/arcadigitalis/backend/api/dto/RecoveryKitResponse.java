package com.arcadigitalis.backend.api.dto;

import java.time.Instant;

/**
 * Recovery kit DTO containing all fields needed for independent beneficiary recovery.
 */
public record RecoveryKitResponse(
        long chainId,
        String proxyAddress,
        String packageKey,
        String status,
        String manifestUri,
        Instant releasedAt,
        Object accCondition,
        String beneficiaryAddress,
        boolean liveRead
) {}
