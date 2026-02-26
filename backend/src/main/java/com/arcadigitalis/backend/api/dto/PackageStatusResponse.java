package com.arcadigitalis.backend.api.dto;

import java.math.BigInteger;
import java.time.Instant;
import java.util.List;

public record PackageStatusResponse(
    Long chainId,
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
    Instant cachedAt,
    boolean liveRead
) {
    public static PackageStatusResponse fromLiveRead(
        Long chainId,
        String proxyAddress,
        String packageKey,
        String status,
        String ownerAddress,
        String beneficiaryAddress,
        List<String> guardians,
        BigInteger guardianQuorum,
        String manifestUri,
        BigInteger warnThreshold,
        BigInteger inactivityThreshold,
        BigInteger gracePeriodSeconds,
        BigInteger lastCheckIn,
        BigInteger paidUntil,
        BigInteger vetoCount,
        BigInteger approvalCount,
        BigInteger pendingSince,
        BigInteger releasedAt
    ) {
        return new PackageStatusResponse(
            chainId,
            proxyAddress,
            packageKey,
            status,
            ownerAddress,
            beneficiaryAddress,
            guardians,
            guardianQuorum != null ? guardianQuorum.intValue() : null,
            manifestUri,
            warnThreshold != null && warnThreshold.compareTo(BigInteger.ZERO) > 0 ? warnThreshold.longValue() : null,
            inactivityThreshold != null && inactivityThreshold.compareTo(BigInteger.ZERO) > 0 ? inactivityThreshold.longValue() : null,
            gracePeriodSeconds != null && gracePeriodSeconds.compareTo(BigInteger.ZERO) > 0 ? gracePeriodSeconds.intValue() : null,
            lastCheckIn != null && lastCheckIn.compareTo(BigInteger.ZERO) > 0 ? Instant.ofEpochSecond(lastCheckIn.longValue()) : null,
            paidUntil != null && paidUntil.compareTo(BigInteger.ZERO) > 0 ? Instant.ofEpochSecond(paidUntil.longValue()) : null,
            vetoCount != null ? vetoCount.intValue() : null,
            approvalCount != null ? approvalCount.intValue() : null,
            pendingSince != null && pendingSince.compareTo(BigInteger.ZERO) > 0 ? Instant.ofEpochSecond(pendingSince.longValue()) : null,
            releasedAt != null && releasedAt.compareTo(BigInteger.ZERO) > 0 ? Instant.ofEpochSecond(releasedAt.longValue()) : null,
            null,
            true
        );
    }
}
