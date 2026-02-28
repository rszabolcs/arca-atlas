package com.arcadigitalis.backend.api.dto;

/**
 * Request body for POST /packages/{key}/tx/rescue.
 */
public record RescueRequest(long chainId, String proxyAddress) {}
