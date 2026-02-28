package com.arcadigitalis.backend.api.dto;

/**
 * Request body for POST /packages/{key}/tx/revoke.
 */
public record RevokeRequest(long chainId, String proxyAddress) {}
