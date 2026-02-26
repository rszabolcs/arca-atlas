package com.arcadigitalis.backend.api.dto;

public record ConfigResponse(
    Long chainId,
    String proxyAddress,
    boolean fundingEnabled
) {
}
