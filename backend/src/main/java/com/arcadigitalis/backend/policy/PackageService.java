package com.arcadigitalis.backend.policy;

import com.arcadigitalis.backend.api.dto.PackageStatusResponse;
import com.arcadigitalis.backend.evm.PolicyReader;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class PackageService {

    private final PolicyReader policyReader;

    @Value("${arca.evm.chain-id}")
    private Long chainId;

    @Value("${arca.evm.policy-proxy-address}")
    private String proxyAddress;

    public PackageService(PolicyReader policyReader) {
        this.policyReader = policyReader;
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
}
