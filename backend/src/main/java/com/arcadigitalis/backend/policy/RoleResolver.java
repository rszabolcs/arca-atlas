package com.arcadigitalis.backend.policy;

import com.arcadigitalis.backend.evm.PolicyReader;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

@Service
public class RoleResolver {

    private final PolicyReader policyReader;

    public RoleResolver(PolicyReader policyReader) {
        this.policyReader = policyReader;
    }

    public void confirmOwner(String packageKey, String sessionAddress) {
        PolicyReader.PackageView packageView = policyReader.getPackage(packageKey);

        if (packageView.ownerAddress() == null ||
            !packageView.ownerAddress().equalsIgnoreCase(sessionAddress)) {
            throw new AccessDeniedException("Caller is not the owner of this package");
        }
    }

    public void confirmGuardian(String packageKey, String sessionAddress) {
        PolicyReader.PackageView packageView = policyReader.getPackage(packageKey);

        boolean isGuardian = packageView.guardians().stream()
            .anyMatch(guardian -> guardian.equalsIgnoreCase(sessionAddress));

        if (!isGuardian) {
            throw new AccessDeniedException("Caller is not a guardian of this package");
        }
    }

    public void confirmBeneficiary(String packageKey, String sessionAddress) {
        PolicyReader.PackageView packageView = policyReader.getPackage(packageKey);

        if (packageView.beneficiaryAddress() == null ||
            !packageView.beneficiaryAddress().equalsIgnoreCase(sessionAddress)) {
            throw new AccessDeniedException("Caller is not the beneficiary of this package");
        }
    }
}
