package com.arcadigitalis.backend.api.dto;

import java.time.Instant;

/**
 * Response for artifact operations.
 */
public record ArtifactResponse(
    String id,
    String artifactType,
    String sha256Hash,
    String ipfsUri,
    String s3Uri,
    long sizeBytes,
    Instant createdAt,
    Instant storageConfirmedAt
) {}
