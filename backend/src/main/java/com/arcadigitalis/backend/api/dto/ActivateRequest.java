package com.arcadigitalis.backend.api.dto;

import java.math.BigInteger;
import java.util.List;

public record ActivateRequest(
    String manifestUri,
    List<String> guardians,
    BigInteger guardianQuorum,
    BigInteger warnThreshold,
    BigInteger inactivityThreshold,
    BigInteger ethValue
) {
}
