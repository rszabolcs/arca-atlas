package com.arcadigitalis.backend.api.dto;

public record ConfigResponse(long chainId, String proxyAddress, boolean fundingEnabled) {}
