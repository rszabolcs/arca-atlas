package com.arcadigitalis.backend.api.controller;

import com.arcadigitalis.backend.api.dto.*;
import com.arcadigitalis.backend.api.exception.ValidationException;
import com.arcadigitalis.backend.policy.TxPayloadService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;

/**
 * Transaction payload endpoints — returns unsigned calldata for client signing.
 * NEVER signs or submits transactions (Constitution IV).
 */
@RestController
@RequestMapping("/packages/{packageKey}/tx")
@Tag(name = "Transactions", description = "Non-custodial unsigned transaction payloads")
public class TxController {

    private static final String PACKAGE_KEY_PATTERN = "^0x[0-9a-fA-F]{64}$";
    private static final int MAX_GUARDIANS = 7;

    private final TxPayloadService txPayloadService;

    public TxController(TxPayloadService txPayloadService) {
        this.txPayloadService = txPayloadService;
    }

    // ── Owner endpoints ───────────────────────────────────────────────────

    @PostMapping("/activate")
    @Operation(summary = "Prepare activate() tx payload", operationId = "prepareActivate")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "Unsigned tx payload"),
                   @ApiResponse(responseCode = "400", description = "Invalid input"),
                   @ApiResponse(responseCode = "409", description = "Package status conflict")})
    public ResponseEntity<TxPayloadResponse> activate(
            @PathVariable String packageKey,
            @RequestBody ActivateRequest request,
            Authentication auth) {
        validatePackageKey(packageKey);
        if (request.guardians() != null && request.guardians().size() > MAX_GUARDIANS) {
            throw new ValidationException("Guardian list exceeds maximum of " + MAX_GUARDIANS);
        }
        TxPayloadResponse response = txPayloadService.prepareActivate(
            packageKey, auth.getName(),
            request.manifestUri(), request.beneficiary(),
            request.guardians() != null ? request.guardians() : Collections.emptyList(),
            request.guardianQuorum() != null ? request.guardianQuorum() : 0,
            request.warnThreshold(), request.inactivityThreshold(),
            request.gracePeriodSeconds(), request.paidUntil(),
            request.ethValue()
        );
        return ResponseEntity.ok(response);
    }

    @PostMapping("/check-in")
    @Operation(summary = "Prepare checkIn() tx payload", operationId = "prepareCheckIn")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "Unsigned tx payload"),
                   @ApiResponse(responseCode = "403", description = "Not the package owner"),
                   @ApiResponse(responseCode = "409", description = "Package status conflict")})
    public ResponseEntity<TxPayloadResponse> checkIn(
            @PathVariable String packageKey,
            @RequestBody CheckInRequest request,
            Authentication auth) {
        validatePackageKey(packageKey);
        TxPayloadResponse response = txPayloadService.prepareCheckIn(packageKey, auth.getName());
        return ResponseEntity.ok(response);
    }

    @PostMapping("/renew")
    @Operation(summary = "Prepare renew() tx payload (unauthenticated)", operationId = "prepareRenew")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "Unsigned tx payload"),
                   @ApiResponse(responseCode = "409", description = "Package status conflict")})
    public ResponseEntity<TxPayloadResponse> renew(
            @PathVariable String packageKey,
            @RequestBody RenewRequest request) {
        validatePackageKey(packageKey);
        TxPayloadResponse response = txPayloadService.prepareRenew(packageKey, request.ethValue());
        return ResponseEntity.ok(response);
    }

    @PostMapping("/update-manifest")
    @Operation(summary = "Prepare updateManifestUri() tx payload", operationId = "prepareUpdateManifestUri")
    public ResponseEntity<TxPayloadResponse> updateManifest(
            @PathVariable String packageKey,
            @RequestBody UpdateManifestRequest request,
            Authentication auth) {
        validatePackageKey(packageKey);
        TxPayloadResponse response = txPayloadService.prepareUpdateManifestUri(
            packageKey, auth.getName(), request.manifestUri()
        );
        return ResponseEntity.ok(response);
    }

    @PostMapping("/revoke")
    @Operation(summary = "Prepare revoke() tx payload", operationId = "prepareRevoke")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "Unsigned tx payload"),
                   @ApiResponse(responseCode = "403", description = "Not the package owner"),
                   @ApiResponse(responseCode = "409", description = "Package status conflict")})
    public ResponseEntity<TxPayloadResponse> revoke(
            @PathVariable String packageKey,
            @RequestBody RevokeRequest request,
            Authentication auth) {
        validatePackageKey(packageKey);
        TxPayloadResponse response = txPayloadService.prepareRevoke(packageKey, auth.getName());
        return ResponseEntity.ok(response);
    }

    @PostMapping("/rescue")
    @Operation(summary = "Prepare rescue() tx payload (unauthenticated)", operationId = "prepareRescue")
    public ResponseEntity<TxPayloadResponse> rescue(
            @PathVariable String packageKey,
            @RequestBody RescueRequest request) {
        validatePackageKey(packageKey);
        TxPayloadResponse response = txPayloadService.prepareRescue(packageKey);
        return ResponseEntity.ok(response);
    }

    // ── Guardian endpoints ────────────────────────────────────────────────

    @PostMapping("/guardian-approve")
    @Operation(summary = "Prepare guardianApprove() tx payload", operationId = "prepareGuardianApprove")
    public ResponseEntity<TxPayloadResponse> guardianApprove(
            @PathVariable String packageKey,
            @RequestBody GuardianTxRequest request,
            Authentication auth) {
        validatePackageKey(packageKey);
        TxPayloadResponse response = txPayloadService.prepareGuardianApprove(packageKey, auth.getName());
        return ResponseEntity.ok(response);
    }

    @PostMapping("/guardian-veto")
    @Operation(summary = "Prepare guardianVeto() tx payload", operationId = "prepareGuardianVeto")
    public ResponseEntity<TxPayloadResponse> guardianVeto(
            @PathVariable String packageKey,
            @RequestBody GuardianTxRequest request,
            Authentication auth) {
        validatePackageKey(packageKey);
        TxPayloadResponse response = txPayloadService.prepareGuardianVeto(packageKey, auth.getName());
        return ResponseEntity.ok(response);
    }

    @PostMapping("/guardian-rescind-veto")
    @Operation(summary = "Prepare guardianRescindVeto() tx payload", operationId = "prepareGuardianRescindVeto")
    public ResponseEntity<TxPayloadResponse> guardianRescindVeto(
            @PathVariable String packageKey,
            @RequestBody GuardianTxRequest request,
            Authentication auth) {
        validatePackageKey(packageKey);
        TxPayloadResponse response = txPayloadService.prepareGuardianRescindVeto(packageKey, auth.getName());
        return ResponseEntity.ok(response);
    }

    @PostMapping("/guardian-rescind-approve")
    @Operation(summary = "Prepare guardianRescindApprove() tx payload", operationId = "prepareGuardianRescindApprove")
    public ResponseEntity<TxPayloadResponse> guardianRescindApprove(
            @PathVariable String packageKey,
            @RequestBody GuardianTxRequest request,
            Authentication auth) {
        validatePackageKey(packageKey);
        TxPayloadResponse response = txPayloadService.prepareGuardianRescindApprove(packageKey, auth.getName());
        return ResponseEntity.ok(response);
    }

    // ── Beneficiary endpoint ──────────────────────────────────────────────

    @PostMapping("/claim")
    @Operation(summary = "Prepare claim() tx payload", operationId = "prepareClaim")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "Unsigned tx payload"),
                   @ApiResponse(responseCode = "403", description = "Not the beneficiary"),
                   @ApiResponse(responseCode = "409", description = "Package not claimable")})
    public ResponseEntity<TxPayloadResponse> claim(
            @PathVariable String packageKey,
            @RequestBody ClaimRequest request,
            Authentication auth) {
        validatePackageKey(packageKey);
        TxPayloadResponse response = txPayloadService.prepareClaimTx(packageKey, auth.getName());
        return ResponseEntity.ok(response);
    }

    // ── Validation ────────────────────────────────────────────────────────

    private void validatePackageKey(String packageKey) {
        if (packageKey == null || !packageKey.matches(PACKAGE_KEY_PATTERN)) {
            throw new ValidationException(
                "Invalid packageKey format: expected 0x-prefixed 64 hex chars (bytes32)"
            );
        }
    }
}
