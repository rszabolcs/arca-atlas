package com.arcadigitalis.backend.api.dto;

import java.util.List;

/**
 * Request body for POST /packages/{key}/tx/activate.
 */
public record ActivateRequest(
    long chainId,
    String proxyAddress,
    String manifestUri,
    String beneficiary,
    long warnThreshold,
    long inactivityThreshold,
    long gracePeriodSeconds,
    long paidUntil,
    List<String> guardians,
    Integer guardianQuorum,
    String ethValue
) {}
