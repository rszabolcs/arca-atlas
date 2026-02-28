package com.arcadigitalis.backend.api.dto;

import java.time.Instant;
import java.util.List;

/**
 * Carries all 15 PackageView fields from the contract's getPackage() return value.
 */
public record PackageStatusResponse(
        long chainId,
        String proxyAddress,
        String packageKey,
        String status,
        String ownerAddress,
        String beneficiaryAddress,
        List<String> guardians,
        Integer guardianQuorum,
        String manifestUri,
        Long warnThreshold,
        Long inactivityThreshold,
        Integer gracePeriodSeconds,
        Instant lastCheckIn,
        Instant paidUntil,
        Integer vetoCount,
        Integer approvalCount,
        Instant pendingSince,
        Instant releasedAt,
        boolean liveRead
) {}
