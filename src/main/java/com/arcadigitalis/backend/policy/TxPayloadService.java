package com.arcadigitalis.backend.policy;

import com.arcadigitalis.backend.api.dto.TxPayloadResponse;
import com.arcadigitalis.backend.api.exception.ConflictException;
import com.arcadigitalis.backend.evm.CalldataBuilder;
import com.arcadigitalis.backend.evm.PolicyReader.PackageView;
import com.arcadigitalis.backend.evm.Web3jConfig;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;

/**
 * Prepares unsigned transaction payloads for all 11 mutating contract functions.
 * Performs role checks via {@link RoleResolver} and status pre-flight checks
 * to return 409 when a transaction is known to revert (FR-012d).
 * NEVER signs or submits transactions (Constitution IV).
 */
@Service
public class TxPayloadService {

    private static final Set<String> TERMINAL_STATUSES = Set.of("RELEASED", "REVOKED");
    private static final String GAS_ESTIMATE = "0x30000"; // 196608 — conservative default

    private final RoleResolver roleResolver;
    private final FundingGuard fundingGuard;
    private final CalldataBuilder calldataBuilder;
    private final Web3jConfig config;

    public TxPayloadService(RoleResolver roleResolver, FundingGuard fundingGuard,
                            CalldataBuilder calldataBuilder, Web3jConfig config) {
        this.roleResolver = roleResolver;
        this.fundingGuard = fundingGuard;
        this.calldataBuilder = calldataBuilder;
        this.config = config;
    }

    // ── Owner endpoints ───────────────────────────────────────────────────

    public TxPayloadResponse prepareActivate(String packageKey, String sessionAddress,
                                              String manifestUri, String beneficiary,
                                              List<String> guardians, int guardianQuorum,
                                              long warnThreshold, long inactivityThreshold,
                                              long gracePeriodSeconds, long paidUntil,
                                              String ethValue) {
        fundingGuard.assertFundingAllowed(ethValue);
        // No owner check for activate — the caller IS the owner-to-be
        String data = calldataBuilder.encodeActivate(
            packageKey, manifestUri, beneficiary, guardians,
            guardianQuorum, warnThreshold, inactivityThreshold,
            gracePeriodSeconds, paidUntil
        );
        return new TxPayloadResponse(config.getProxyAddress(), data, ethValue != null ? ethValue : "0x0", GAS_ESTIMATE);
    }

    public TxPayloadResponse prepareCheckIn(String packageKey, String sessionAddress) {
        PackageView view = roleResolver.confirmOwner(packageKey, sessionAddress);
        // Pre-flight: 409 on CLAIMABLE, RELEASED, REVOKED
        if ("CLAIMABLE".equals(view.status())) {
            throw new ConflictException("Package is CLAIMABLE — checkIn will revert on-chain with AlreadyClaimable()", view.status());
        }
        assertNotTerminal(view.status(), "checkIn");
        String data = calldataBuilder.encodeCheckIn(packageKey);
        return new TxPayloadResponse(config.getProxyAddress(), data, GAS_ESTIMATE);
    }

    public TxPayloadResponse prepareRenew(String packageKey, String ethValue) {
        // Unauthenticated — no owner check, no status pre-flight (FR-012d)
        fundingGuard.assertFundingAllowed(ethValue);
        String data = calldataBuilder.encodeRenew(packageKey);
        return new TxPayloadResponse(config.getProxyAddress(), data, ethValue != null ? ethValue : "0x0", GAS_ESTIMATE);
    }

    public TxPayloadResponse prepareUpdateManifestUri(String packageKey, String sessionAddress,
                                                       String newManifestUri) {
        PackageView view = roleResolver.confirmOwner(packageKey, sessionAddress);
        assertNotTerminal(view.status(), "updateManifestUri");
        String data = calldataBuilder.encodeUpdateManifestUri(packageKey, newManifestUri);
        return new TxPayloadResponse(config.getProxyAddress(), data, GAS_ESTIMATE);
    }

    public TxPayloadResponse prepareRevoke(String packageKey, String sessionAddress) {
        PackageView view = roleResolver.confirmOwner(packageKey, sessionAddress);
        assertNotTerminal(view.status(), "revoke");
        String data = calldataBuilder.encodeRevoke(packageKey);
        return new TxPayloadResponse(config.getProxyAddress(), data, GAS_ESTIMATE);
    }

    public TxPayloadResponse prepareRescue(String packageKey) {
        // Unauthenticated — contract enforces NotAuthorized(); no status pre-flight
        String data = calldataBuilder.encodeRescue(packageKey);
        return new TxPayloadResponse(config.getProxyAddress(), data, GAS_ESTIMATE);
    }

    // ── Guardian endpoints ────────────────────────────────────────────────

    public TxPayloadResponse prepareGuardianApprove(String packageKey, String sessionAddress) {
        PackageView view = roleResolver.confirmGuardian(packageKey, sessionAddress);
        assertPendingRelease(view.status(), "guardianApprove");
        String data = calldataBuilder.encodeGuardianApprove(packageKey);
        return new TxPayloadResponse(config.getProxyAddress(), data, GAS_ESTIMATE);
    }

    public TxPayloadResponse prepareGuardianVeto(String packageKey, String sessionAddress) {
        PackageView view = roleResolver.confirmGuardian(packageKey, sessionAddress);
        assertPendingRelease(view.status(), "guardianVeto");
        String data = calldataBuilder.encodeGuardianVeto(packageKey);
        return new TxPayloadResponse(config.getProxyAddress(), data, GAS_ESTIMATE);
    }

    public TxPayloadResponse prepareGuardianRescindVeto(String packageKey, String sessionAddress) {
        PackageView view = roleResolver.confirmGuardian(packageKey, sessionAddress);
        assertNotTerminal(view.status(), "guardianRescindVeto");
        String data = calldataBuilder.encodeGuardianRescindVeto(packageKey);
        return new TxPayloadResponse(config.getProxyAddress(), data, GAS_ESTIMATE);
    }

    public TxPayloadResponse prepareGuardianRescindApprove(String packageKey, String sessionAddress) {
        PackageView view = roleResolver.confirmGuardian(packageKey, sessionAddress);
        assertNotTerminal(view.status(), "guardianRescindApprove");
        String data = calldataBuilder.encodeGuardianRescindApprove(packageKey);
        return new TxPayloadResponse(config.getProxyAddress(), data, GAS_ESTIMATE);
    }

    // ── Beneficiary endpoints ─────────────────────────────────────────────

    public TxPayloadResponse prepareClaimTx(String packageKey, String sessionAddress) {
        PackageView view = roleResolver.confirmBeneficiary(packageKey, sessionAddress);
        if (!"CLAIMABLE".equals(view.status())) {
            throw new ConflictException("Package is not CLAIMABLE — current status: " + view.status(), view.status());
        }
        String data = calldataBuilder.encodeClaim(packageKey);
        return new TxPayloadResponse(config.getProxyAddress(), data, GAS_ESTIMATE);
    }

    // ── Pre-flight helpers ────────────────────────────────────────────────

    private void assertNotTerminal(String status, String operation) {
        if (TERMINAL_STATUSES.contains(status)) {
            throw new ConflictException(
                "Package is " + status + " — " + operation + "() will revert on-chain",
                status
            );
        }
    }

    private void assertPendingRelease(String status, String operation) {
        if (TERMINAL_STATUSES.contains(status) || !"PENDING_RELEASE".equals(status)) {
            throw new ConflictException(
                "Package is not PENDING_RELEASE (current: " + status + ") — " + operation + "() will revert on-chain",
                status
            );
        }
    }
}
