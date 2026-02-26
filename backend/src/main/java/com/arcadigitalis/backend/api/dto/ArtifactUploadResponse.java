package com.arcadigitalis.backend.api.dto;

import java.util.UUID;

public record ArtifactUploadResponse(
    UUID id,
    String confirmedUri,
    String sha256Hash,
    Long sizeBytes
) {
}
