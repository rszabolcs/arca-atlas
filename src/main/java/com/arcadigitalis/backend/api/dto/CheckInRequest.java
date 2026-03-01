package com.arcadigitalis.backend.api.dto;

/**
 * Request body for POST /packages/{key}/tx/check-in.
 */
public record CheckInRequest(long chainId, String proxyAddress) {}
