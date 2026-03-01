package com.arcadigitalis.backend.api.dto;

/**
 * Request body for POST /packages/{key}/tx/claim.
 */
public record ClaimRequest(long chainId, String proxyAddress) {}
