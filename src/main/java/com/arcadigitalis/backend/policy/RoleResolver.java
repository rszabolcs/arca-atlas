package com.arcadigitalis.backend.policy;

import com.arcadigitalis.backend.evm.PolicyReader;
import com.arcadigitalis.backend.evm.PolicyReader.PackageView;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

/**
 * Resolves roles (owner, guardian, beneficiary) from the live chain.
 * NEVER reads from guardian_cache or package_cache for authorization decisions
 * (Constitution III — On-Chain Authoritativeness).
 */
@Component
public class RoleResolver {

    private final PolicyReader policyReader;

    public RoleResolver(PolicyReader policyReader) {
        this.policyReader = policyReader;
    }

    /**
     * Confirms the session address is the on-chain owner.
     * Returns the live PackageView for reuse (avoids duplicate RPC call).
     * @throws AccessDeniedException if not the owner
     */
    public PackageView confirmOwner(String packageKey, String sessionAddress) {
        PackageView view = policyReader.getPackage(packageKey);
        if (view.ownerAddress() == null || !view.ownerAddress().equalsIgnoreCase(sessionAddress)) {
            throw new AccessDeniedException("Caller is not the on-chain owner of package " + packageKey);
        }
        return view;
    }

    /**
     * Confirms the session address is an on-chain guardian.
     * Returns the live PackageView for reuse.
     * @throws AccessDeniedException if not a guardian
     */
    public PackageView confirmGuardian(String packageKey, String sessionAddress) {
        PackageView view = policyReader.getPackage(packageKey);
        boolean isGuardian = view.guardians().stream()
            .anyMatch(g -> g.equalsIgnoreCase(sessionAddress));
        if (!isGuardian) {
            throw new AccessDeniedException("Caller is not a guardian for package " + packageKey);
        }
        return view;
    }

    /**
     * Confirms the session address is the on-chain beneficiary.
     * Returns the live PackageView for reuse.
     * @throws AccessDeniedException if not the beneficiary
     */
    public PackageView confirmBeneficiary(String packageKey, String sessionAddress) {
        PackageView view = policyReader.getPackage(packageKey);
        if (view.beneficiaryAddress() == null || !view.beneficiaryAddress().equalsIgnoreCase(sessionAddress)) {
            throw new AccessDeniedException("Caller is not the beneficiary of package " + packageKey);
        }
        return view;
    }
}
