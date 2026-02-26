package com.arcadigitalis.backend.api.controller;

import com.arcadigitalis.backend.api.dto.*;
import com.arcadigitalis.backend.policy.TxPayloadService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/packages/{packageKey}/tx")
public class TxController {

    private final TxPayloadService txPayloadService;

    public TxController(TxPayloadService txPayloadService) {
        this.txPayloadService = txPayloadService;
    }

    @PostMapping("/activate")
    public TxPayloadResponse activate(
        @PathVariable String packageKey,
        @RequestBody ActivateRequest request,
        @RequestAttribute("sessionAddress") String sessionAddress
    ) {
        return txPayloadService.prepareActivate(
            packageKey,
            sessionAddress,
            request.manifestUri(),
            request.guardians(),
            request.guardianQuorum(),
            request.warnThreshold(),
            request.inactivityThreshold(),
            request.ethValue()
        );
    }

    @PostMapping("/check-in")
    public TxPayloadResponse checkIn(
        @PathVariable String packageKey,
        @RequestAttribute("sessionAddress") String sessionAddress
    ) {
        return txPayloadService.prepareCheckIn(packageKey, sessionAddress);
    }

    @PostMapping("/renew")
    public TxPayloadResponse renew(
        @PathVariable String packageKey,
        @RequestBody(required = false) RenewRequest request
    ) {
        return txPayloadService.prepareRenew(packageKey, request != null ? request.ethValue() : null);
    }

    @PostMapping("/update-manifest")
    public TxPayloadResponse updateManifest(
        @PathVariable String packageKey,
        @RequestBody UpdateManifestRequest request,
        @RequestAttribute("sessionAddress") String sessionAddress
    ) {
        return txPayloadService.prepareUpdateManifestUri(packageKey, sessionAddress, request.newManifestUri());
    }

    @PostMapping("/revoke")
    public TxPayloadResponse revoke(
        @PathVariable String packageKey,
        @RequestAttribute("sessionAddress") String sessionAddress
    ) {
        return txPayloadService.prepareRevoke(packageKey, sessionAddress);
    }

    @PostMapping("/rescue")
    public TxPayloadResponse rescue(@PathVariable String packageKey) {
        return txPayloadService.prepareRescue(packageKey);
    }

    // Guardian operations
    @PostMapping("/guardian-approve")
    public TxPayloadResponse guardianApprove(
        @PathVariable String packageKey,
        @RequestAttribute("sessionAddress") String sessionAddress
    ) {
        return txPayloadService.prepareGuardianApprove(packageKey, sessionAddress);
    }

    @PostMapping("/guardian-veto")
    public TxPayloadResponse guardianVeto(
        @PathVariable String packageKey,
        @RequestAttribute("sessionAddress") String sessionAddress
    ) {
        return txPayloadService.prepareGuardianVeto(packageKey, sessionAddress);
    }

    @PostMapping("/guardian-rescind-veto")
    public TxPayloadResponse guardianRescindVeto(
        @PathVariable String packageKey,
        @RequestAttribute("sessionAddress") String sessionAddress
    ) {
        return txPayloadService.prepareGuardianRescindVeto(packageKey, sessionAddress);
    }

    @PostMapping("/guardian-rescind-approve")
    public TxPayloadResponse guardianRescindApprove(
        @PathVariable String packageKey,
        @RequestAttribute("sessionAddress") String sessionAddress
    ) {
        return txPayloadService.prepareGuardianRescindApprove(packageKey, sessionAddress);
    }

    // Beneficiary claim
    @PostMapping("/claim")
    public TxPayloadResponse claim(
        @PathVariable String packageKey,
        @RequestAttribute("sessionAddress") String sessionAddress
    ) {
        return txPayloadService.prepareClaimTx(packageKey, sessionAddress);
    }
}
