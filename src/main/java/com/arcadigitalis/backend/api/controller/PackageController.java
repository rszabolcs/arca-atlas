package com.arcadigitalis.backend.api.controller;

import com.arcadigitalis.backend.api.dto.PackageStatusResponse;
import com.arcadigitalis.backend.api.dto.RecoveryKitResponse;
import com.arcadigitalis.backend.api.exception.ValidationException;
import com.arcadigitalis.backend.policy.PackageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Package status and recovery kit endpoints.
 */
@RestController
@RequestMapping("/packages")
@Tag(name = "Packages", description = "Package status and beneficiary recovery")
public class PackageController {

    private static final String PACKAGE_KEY_PATTERN = "^0x[0-9a-fA-F]{64}$";

    private final PackageService packageService;

    public PackageController(PackageService packageService) {
        this.packageService = packageService;
    }

    /**
     * Live package status read (FR-008, FR-009, FR-009a).
     * Unauthenticated per openapi.yaml (security: []).
     */
    @GetMapping("/{packageKey}/status")
    @Operation(summary = "Get live package status", operationId = "getPackageStatus")
    public ResponseEntity<PackageStatusResponse> getStatus(@PathVariable String packageKey) {
        validatePackageKey(packageKey);
        PackageStatusResponse response = packageService.getPackageView(packageKey);
        return ResponseEntity.ok(response);
    }

    /**
     * Recovery kit (FR-010, FR-015, FR-016). Unauthenticated.
     */
    @GetMapping("/{packageKey}/recovery-kit")
    @Operation(summary = "Get beneficiary recovery kit", operationId = "getRecoveryKit")
    public ResponseEntity<RecoveryKitResponse> getRecoveryKit(@PathVariable String packageKey) {
        validatePackageKey(packageKey);
        RecoveryKitResponse response = packageService.getRecoveryKit(packageKey);
        return ResponseEntity.ok(response);
    }

    private void validatePackageKey(String packageKey) {
        if (packageKey == null || !packageKey.matches(PACKAGE_KEY_PATTERN)) {
            throw new ValidationException(
                "Invalid packageKey format: expected 0x-prefixed 64 hex chars (bytes32)"
            );
        }
    }
}
