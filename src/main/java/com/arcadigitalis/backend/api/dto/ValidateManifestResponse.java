package com.arcadigitalis.backend.api.dto;

import java.util.List;
import java.util.Map;

/**
 * Response for POST /validate-manifest.
 */
public record ValidateManifestResponse(boolean valid, Map<String, Boolean> checks, List<String> errors) {}
