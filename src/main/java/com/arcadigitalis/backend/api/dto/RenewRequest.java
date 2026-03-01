package com.arcadigitalis.backend.api.dto;

/**
 * Request body for POST /packages/{key}/tx/renew.
 */
public record RenewRequest(long chainId, String proxyAddress, String ethValue) {}
