package com.arcadigitalis.backend.policy;

import com.arcadigitalis.backend.api.dto.TxPayloadResponse;
import com.arcadigitalis.backend.api.exception.ConflictException;
import com.arcadigitalis.backend.evm.CalldataBuilder;
import com.arcadigitalis.backend.evm.PolicyReader;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigInteger;
import java.util.List;

@Service
public class TxPayloadService {

    private final CalldataBuilder calldataBuilder;
    private final RoleResolver roleResolver;
    private final FundingGuard fundingGuard;
    private final PolicyReader policyReader;

    @Value("${arca.evm.policy-proxy-address}")
    private String proxyAddress;

    public TxPayloadService(CalldataBuilder calldataBuilder, RoleResolver roleResolver,
                             FundingGuard fundingGuard, PolicyReader policyReader) {
        this.calldataBuilder = calldataBuilder;
        this.roleResolver = roleResolver;
        this.fundingGuard = fundingGuard;
        this.policyReader = policyReader;
    }

    public TxPayloadResponse prepareActivate(String packageKey, String sessionAddress,
                                               String manifestUri, List<String> guardians,
                                               BigInteger guardianQuorum, BigInteger warnThreshold,
                                               BigInteger inactivityThreshold, BigInteger ethValue) {
        roleResolver.confirmOwner(packageKey, sessionAddress);
        fundingGuard.assertFundingAllowed(ethValue);

        if (guardians.size() > 7) {
            throw new ConflictException("Guardian list cannot exceed 7 entries");
        }

        String calldata = calldataBuilder.encodeActivate(packageKey, manifestUri, guardians,
            guardianQuorum, warnThreshold, inactivityThreshold);

        return new TxPayloadResponse(proxyAddress, calldata, ethValue != null ? "0x" + ethValue.toString(16) : null, "150000");
    }

    public TxPayloadResponse prepareCheckIn(String packageKey, String sessionAddress) {
        roleResolver.confirmOwner(packageKey, sessionAddress);

        // Pre-flight: 409 on CLAIMABLE/RELEASED/REVOKED
        String status = policyReader.getPackageStatus(packageKey);
        if ("CLAIMABLE".equals(status) || "RELEASED".equals(status) || "REVOKED".equals(status)) {
            throw new ConflictException("Cannot check in: package is in " + status + " status");
        }

        String calldata = calldataBuilder.encodeCheckIn(packageKey);
        return new TxPayloadResponse(proxyAddress, calldata, null, "50000");
    }

    public TxPayloadResponse prepareRenew(String packageKey, BigInteger ethValue) {
        // Unauthenticated endpoint - no role check
        fundingGuard.assertFundingAllowed(ethValue);

        String calldata = calldataBuilder.encodeRenew(packageKey);
        return new TxPayloadResponse(proxyAddress, calldata, ethValue != null ? "0x" + ethValue.toString(16) : null, "50000");
    }

    public TxPayloadResponse prepareUpdateManifestUri(String packageKey, String sessionAddress, String newManifestUri) {
        roleResolver.confirmOwner(packageKey, sessionAddress);

        // Pre-flight: 409 on RELEASED/REVOKED
        String status = policyReader.getPackageStatus(packageKey);
        if ("RELEASED".equals(status) || "REVOKED".equals(status)) {
            throw new ConflictException("Cannot update manifest: package is in " + status + " status");
        }

        String calldata = calldataBuilder.encodeUpdateManifestUri(packageKey, newManifestUri);
        return new TxPayloadResponse(proxyAddress, calldata, null, "60000");
    }

    public TxPayloadResponse prepareRevoke(String packageKey, String sessionAddress) {
        roleResolver.confirmOwner(packageKey, sessionAddress);

        // Pre-flight: 409 on RELEASED/REVOKED
        String status = policyReader.getPackageStatus(packageKey);
        if ("RELEASED".equals(status) || "REVOKED".equals(status)) {
            throw new ConflictException("Cannot revoke: package is in " + status + " status");
        }

        String calldata = calldataBuilder.encodeRevoke(packageKey);
        return new TxPayloadResponse(proxyAddress, calldata, null, "50000");
    }

    public TxPayloadResponse prepareRescue(String packageKey) {
        // Unauthenticated endpoint - no role check
        String calldata = calldataBuilder.encodeRescue(packageKey);
        return new TxPayloadResponse(proxyAddress, calldata, null, "50000");
    }

    // Guardian operations
    public TxPayloadResponse prepareGuardianApprove(String packageKey, String sessionAddress) {
        roleResolver.confirmGuardian(packageKey, sessionAddress);

        String status = policyReader.getPackageStatus(packageKey);
        if (!"PENDING_RELEASE".equals(status)) {
            throw new ConflictException("Cannot approve: package is not in PENDING_RELEASE status");
        }

        String calldata = calldataBuilder.encodeGuardianApprove(packageKey);
        return new TxPayloadResponse(proxyAddress, calldata, null, "60000");
    }

    public TxPayloadResponse prepareGuardianVeto(String packageKey, String sessionAddress) {
        roleResolver.confirmGuardian(packageKey, sessionAddress);

        String status = policyReader.getPackageStatus(packageKey);
        if (!"PENDING_RELEASE".equals(status)) {
            throw new ConflictException("Cannot veto: package is not in PENDING_RELEASE status");
        }

        String calldata = calldataBuilder.encodeGuardianVeto(packageKey);
        return new TxPayloadResponse(proxyAddress, calldata, null, "60000");
    }

    public TxPayloadResponse prepareGuardianRescindVeto(String packageKey, String sessionAddress) {
        roleResolver.confirmGuardian(packageKey, sessionAddress);

        String status = policyReader.getPackageStatus(packageKey);
        if (!"PENDING_RELEASE".equals(status)) {
            throw new ConflictException("Cannot rescind veto: package is not in PENDING_RELEASE status");
        }

        String calldata = calldataBuilder.encodeGuardianRescindVeto(packageKey);
        return new TxPayloadResponse(proxyAddress, calldata, null, "60000");
    }

    public TxPayloadResponse prepareGuardianRescindApprove(String packageKey, String sessionAddress) {
        roleResolver.confirmGuardian(packageKey, sessionAddress);

        String status = policyReader.getPackageStatus(packageKey);
        if (!"PENDING_RELEASE".equals(status)) {
            throw new ConflictException("Cannot rescind approve: package is not in PENDING_RELEASE status");
        }

        String calldata = calldataBuilder.encodeGuardianRescindApprove(packageKey);
        return new TxPayloadResponse(proxyAddress, calldata, null, "60000");
    }

    // Beneficiary claim
    public TxPayloadResponse prepareClaimTx(String packageKey, String sessionAddress) {
        roleResolver.confirmBeneficiary(packageKey, sessionAddress);

        String status = policyReader.getPackageStatus(packageKey);
        if (!"CLAIMABLE".equals(status)) {
            throw new ConflictException("Cannot claim: package is not in CLAIMABLE status (current: " + status + ")");
        }

        String calldata = calldataBuilder.encodeClaim(packageKey);
        return new TxPayloadResponse(proxyAddress, calldata, null, "80000");
    }
}
