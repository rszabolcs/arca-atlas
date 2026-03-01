package com.arcadigitalis.backend.api.dto;

/**
 * Request body for POST /packages/{key}/tx/update-manifest.
 */
public record UpdateManifestRequest(long chainId, String proxyAddress, String manifestUri) {}
