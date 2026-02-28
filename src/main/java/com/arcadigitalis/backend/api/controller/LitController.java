package com.arcadigitalis.backend.api.controller;

import com.arcadigitalis.backend.api.dto.ValidateManifestResponse;
import com.arcadigitalis.backend.api.exception.ValidationException;
import com.arcadigitalis.backend.lit.AccTemplateBuilder;
import com.arcadigitalis.backend.lit.ManifestValidator;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Lit ACC template generation and manifest validation endpoints.
 * Zero Lit network calls (research §3).
 */
@RestController
@Tag(name = "Lit Integration", description = "ACC template and manifest validation")
public class LitController {

    private final AccTemplateBuilder accTemplateBuilder;
    private final ManifestValidator manifestValidator;

    public LitController(AccTemplateBuilder accTemplateBuilder, ManifestValidator manifestValidator) {
        this.accTemplateBuilder = accTemplateBuilder;
        this.manifestValidator = manifestValidator;
    }

    /**
     * GET /acc-template — generates a Lit ACC JSON for the given parameters.
     * Unauthenticated per openapi.yaml (security: []).
     */
    @GetMapping("/acc-template")
    @Operation(summary = "Generate a Lit ACC template for a package", operationId = "getAccTemplate")
    public ResponseEntity<ObjectNode> getAccTemplate(
            @RequestParam long chainId,
            @RequestParam String proxyAddress,
            @RequestParam String packageKey,
            @RequestParam String beneficiary) {
        if (packageKey == null || packageKey.isBlank()) {
            throw new ValidationException("packageKey is required");
        }
        if (beneficiary == null || beneficiary.isBlank()) {
            throw new ValidationException("beneficiary is required");
        }
        ObjectNode acc = accTemplateBuilder.buildAccTemplate(chainId, proxyAddress, packageKey, beneficiary);
        return ResponseEntity.ok(acc);
    }

    /**
     * POST /validate-manifest — 3-layer manifest validation (FR-020).
     * Unauthenticated per openapi.yaml (security: []).
     */
    @PostMapping("/validate-manifest")
    @Operation(summary = "Validate a vault manifest (3-layer check)", operationId = "validateManifest")
    public ResponseEntity<ValidateManifestResponse> validateManifest(@RequestBody JsonNode manifest) {
        if (manifest == null || manifest.isEmpty()) {
            throw new ValidationException("Manifest body is required");
        }
        ManifestValidator.ValidationResult result = manifestValidator.validate(manifest);
        return ResponseEntity.ok(new ValidateManifestResponse(result.valid(), result.checks(), result.errors()));
    }
}
