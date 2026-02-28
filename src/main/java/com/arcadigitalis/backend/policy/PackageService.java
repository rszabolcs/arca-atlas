package com.arcadigitalis.backend.policy;

import com.arcadigitalis.backend.api.dto.PackageStatusResponse;
import com.arcadigitalis.backend.api.dto.RecoveryKitResponse;
import com.arcadigitalis.backend.evm.PolicyReader;
import com.arcadigitalis.backend.evm.PolicyReader.PackageView;
import com.arcadigitalis.backend.evm.Web3jConfig;
import com.arcadigitalis.backend.lit.AccTemplateBuilder;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Maps contract read data to API DTOs. Always performs a live chain read;
 * the DB cache is NEVER the authoritative source for status (Constitution III).
 * Returns {@code DRAFT} for unknown package keys (FR-009).
 * Surfaces {@code WARNING} and {@code CLAIMABLE} as-is (FR-009a).
 */
@Service
public class PackageService {

    private static final Logger log = LoggerFactory.getLogger(PackageService.class);

    private final PolicyReader policyReader;
    private final Web3jConfig config;
    private final AccTemplateBuilder accTemplateBuilder;

    public PackageService(PolicyReader policyReader, Web3jConfig config, AccTemplateBuilder accTemplateBuilder) {
        this.policyReader = policyReader;
        this.config = config;
        this.accTemplateBuilder = accTemplateBuilder;
    }

    /**
     * Gets full package view from live chain read and maps to
     * PackageStatusResponse.
     */
    public PackageStatusResponse getPackageView(String packageKey) {
        PackageView view = policyReader.getPackage(packageKey);
        return new PackageStatusResponse(
            config.getChainId(),
            config.getProxyAddress(),
            packageKey,
            view.status(),
            view.ownerAddress(),
            view.beneficiaryAddress(),
            view.guardians(),
            view.guardianQuorum(),
            view.manifestUri(),
            view.warnThreshold(),
            view.inactivityThreshold(),
            view.gracePeriodSeconds(),
            view.lastCheckIn(),
            view.paidUntil(),
            view.vetoCount(),
            view.approvalCount(),
            view.pendingSince(),
            view.releasedAt(),
            true // liveRead — always true for this path
        );
    }

    /**
     * Gets a recovery kit for the beneficiary. Always performs a live chain
     * read; never errors because DB is empty (FR-016).
     */
    public RecoveryKitResponse getRecoveryKit(String packageKey) {
        PackageView view = policyReader.getPackage(packageKey);

        Object accCondition = null;
        if (view.beneficiaryAddress() != null && !view.beneficiaryAddress().isBlank()
                && !isZeroAddress(view.beneficiaryAddress())) {
            try {
                ObjectNode acc = accTemplateBuilder.buildAccTemplate(
                    config.getChainId(),
                    config.getProxyAddress(),
                    packageKey,
                    view.beneficiaryAddress()
                );
                accCondition = acc;
            } catch (Exception e) {
                log.warn("Could not build ACC template for packageKey={}: {}", packageKey, e.getMessage());
            }
        }

        return new RecoveryKitResponse(
            config.getChainId(),
            config.getProxyAddress(),
            packageKey,
            view.status(),
            view.manifestUri(),
            view.releasedAt(),
            accCondition,
            view.beneficiaryAddress(),
            true // liveRead
        );
    }

    private static boolean isZeroAddress(String address) {
        return "0x0000000000000000000000000000000000000000".equalsIgnoreCase(address);
    }
}
