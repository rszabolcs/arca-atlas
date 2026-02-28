package com.arcadigitalis.backend.api.controller;

import com.arcadigitalis.backend.api.dto.ArtifactResponse;
import com.arcadigitalis.backend.api.exception.ValidationException;
import com.arcadigitalis.backend.storage.ArtifactService;
import com.arcadigitalis.backend.storage.ArtifactService.ArtifactData;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.UUID;

/**
 * Artifact storage endpoints — pins manifest/ciphertext blobs with sha256 integrity.
 * NEVER decrypts or inspects blob content (Constitution I).
 */
@RestController
@RequestMapping("/artifacts")
@Tag(name = "Storage", description = "Encrypted artifact storage and integrity")
public class StorageController {

    private final ArtifactService artifactService;

    public StorageController(ArtifactService artifactService) {
        this.artifactService = artifactService;
    }

    /**
     * POST /artifacts — pin artifact with sha256 verification.
     */
    @PostMapping
    @Operation(summary = "Pin manifest or ciphertext artifact", operationId = "pinArtifact")
    @ApiResponses({@ApiResponse(responseCode = "201", description = "Artifact pinned"),
                   @ApiResponse(responseCode = "400", description = "Invalid input"),
                   @ApiResponse(responseCode = "422", description = "SHA-256 integrity check failed")})
    public ResponseEntity<ArtifactResponse> pinArtifact(
            @RequestParam String artifactType,
            @RequestParam String sha256Hash,
            @RequestParam(required = false) Long chainId,
            @RequestParam(required = false) String proxyAddress,
            @RequestParam(required = false) String packageKey,
            @RequestPart("content") MultipartFile content) throws IOException {

        if (!"manifest".equals(artifactType) && !"ciphertext".equals(artifactType)) {
            throw new ValidationException("artifactType must be 'manifest' or 'ciphertext'");
        }
        if (sha256Hash == null || sha256Hash.isBlank()) {
            throw new ValidationException("sha256Hash is required");
        }

        byte[] bytes = content.getBytes();
        ArtifactData data = artifactService.pin(artifactType, sha256Hash, bytes, chainId, proxyAddress, packageKey);
        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(data));
    }

    /**
     * GET /artifacts/{id} — retrieve artifact metadata by ID.
     */
    @GetMapping("/{id}")
    @Operation(summary = "Retrieve artifact metadata by ID", operationId = "getArtifact")
    public ResponseEntity<ArtifactResponse> getArtifact(@PathVariable UUID id) {
        return artifactService.retrieve(id)
            .map(data -> ResponseEntity.ok(toResponse(data)))
            .orElse(ResponseEntity.notFound().build());
    }

    private ArtifactResponse toResponse(ArtifactData data) {
        return new ArtifactResponse(
            data.id(),
            data.artifactType(),
            data.sha256Hash(),
            data.ipfsUri(),
            data.s3Uri(),
            data.sizeBytes(),
            data.createdAt(),
            data.storageConfirmedAt()
        );
    }
}
