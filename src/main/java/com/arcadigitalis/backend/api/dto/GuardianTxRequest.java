package com.arcadigitalis.backend.api.dto;

/**
 * Request body for guardian tx endpoints (approve, veto, rescind-veto, rescind-approve).
 */
public record GuardianTxRequest(long chainId, String proxyAddress) {}
