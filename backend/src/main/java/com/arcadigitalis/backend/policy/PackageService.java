package com.arcadigitalis.backend.policy;

import com.arcadigitalis.backend.api.dto.PackageStatusResponse;
import com.arcadigitalis.backend.api.dto.RecoveryKitResponse;
import com.arcadigitalis.backend.evm.PolicyReader;
import com.arcadigitalis.backend.lit.AccTemplateBuilder;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigInteger;
import java.time.Instant;

@Service
public class PackageService {

    private final PolicyReader policyReader;
    private final AccTemplateBuilder accTemplateBuilder;

    @Value("${arca.evm.chain-id}")
    private Long chainId;

    @Value("${arca.evm.policy-proxy-address}")
    private String proxyAddress;

    public PackageService(PolicyReader policyReader, AccTemplateBuilder accTemplateBuilder) {
        this.policyReader = policyReader;
        this.accTemplateBuilder = accTemplateBuilder;
    }

    public PackageStatusResponse getPackageView(String packageKey) {
        // Live chain read - never cached for status queries
        PolicyReader.PackageView packageView = policyReader.getPackage(packageKey);

        return PackageStatusResponse.fromLiveRead(
            chainId,
            proxyAddress,
            packageKey,
            packageView.status(),
            packageView.ownerAddress(),
            packageView.beneficiaryAddress(),
            packageView.guardians(),
            packageView.guardianQuorum(),
            packageView.manifestUri(),
            packageView.warnThreshold(),
            packageView.inactivityThreshold(),
            packageView.gracePeriodSeconds(),
            packageView.lastCheckIn(),
            packageView.paidUntil(),
            packageView.vetoCount(),
            packageView.approvalCount(),
            packageView.pendingSince(),
            packageView.releasedAt()
        );
    }

    public RecoveryKitResponse getRecoveryKit(String packageKey) {
        // Always live chain read (FR-016: works even with empty DB)
        PolicyReader.PackageView packageView = policyReader.getPackage(packageKey);

        ObjectNode accCondition = null;
        if (packageView.beneficiaryAddress() != null) {
            accCondition = accTemplateBuilder.buildAccTemplate(
                chainId,
                proxyAddress,
                packageKey,
                packageView.beneficiaryAddress()
            );
        }

        Instant releasedAt = null;
        if (packageView.releasedAt() != null && packageView.releasedAt().compareTo(BigInteger.ZERO) > 0) {
            releasedAt = Instant.ofEpochSecond(packageView.releasedAt().longValue());
        }

        return new RecoveryKitResponse(
            chainId,
            proxyAddress,
            packageKey,
            packageView.status(),
            packageView.manifestUri(),
            releasedAt,
            accCondition,
            packageView.beneficiaryAddress(),
            true
        );
    }
}
