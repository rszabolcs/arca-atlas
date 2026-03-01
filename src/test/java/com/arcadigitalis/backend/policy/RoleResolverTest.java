package com.arcadigitalis.backend.policy;

import com.arcadigitalis.backend.evm.PolicyReader;
import com.arcadigitalis.backend.evm.PolicyReader.PackageView;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/**
 * Unit tests for RoleResolver — T054.
 * Tests: confirmOwner/Guardian/Beneficiary happy + mismatch paths.
 */
@ExtendWith(MockitoExtension.class)
class RoleResolverTest {

    @Mock private PolicyReader policyReader;

    private RoleResolver resolver;

    private static final String PKG_KEY = "0x" + "ab".repeat(32);
    private static final String OWNER = "0x71C7656EC7ab88b098defB751B7401B5f6d8976F";
    private static final String GUARDIAN = "0x2222222222222222222222222222222222222222";
    private static final String BENEFICIARY = "0x3333333333333333333333333333333333333333";
    private static final String STRANGER = "0x9999999999999999999999999999999999999999";

    @BeforeEach
    void setUp() {
        resolver = new RoleResolver(policyReader);
    }

    private PackageView buildView() {
        return new PackageView(
            "ACTIVE", OWNER, BENEFICIARY, "ipfs://Qm",
            List.of(GUARDIAN), 1,
            0, 0,
            86400L, 604800L, 259200,
            null, null, null, null
        );
    }

    @Test
    @DisplayName("confirmOwner returns PackageView when caller is owner")
    void confirmOwner_success() {
        when(policyReader.getPackage(PKG_KEY)).thenReturn(buildView());
        PackageView result = resolver.confirmOwner(PKG_KEY, OWNER);
        assertThat(result.ownerAddress()).isEqualTo(OWNER);
    }

    @Test
    @DisplayName("confirmOwner is case-insensitive")
    void confirmOwner_caseInsensitive() {
        when(policyReader.getPackage(PKG_KEY)).thenReturn(buildView());
        PackageView result = resolver.confirmOwner(PKG_KEY, OWNER.toLowerCase());
        assertThat(result).isNotNull();
    }

    @Test
    @DisplayName("confirmOwner throws AccessDeniedException for non-owner")
    void confirmOwner_nonOwner_throws() {
        when(policyReader.getPackage(PKG_KEY)).thenReturn(buildView());
        assertThatThrownBy(() -> resolver.confirmOwner(PKG_KEY, STRANGER))
            .isInstanceOf(AccessDeniedException.class)
            .hasMessageContaining("owner");
    }

    @Test
    @DisplayName("confirmGuardian returns PackageView when caller is guardian")
    void confirmGuardian_success() {
        when(policyReader.getPackage(PKG_KEY)).thenReturn(buildView());
        PackageView result = resolver.confirmGuardian(PKG_KEY, GUARDIAN);
        assertThat(result).isNotNull();
    }

    @Test
    @DisplayName("confirmGuardian throws for non-guardian")
    void confirmGuardian_nonGuardian_throws() {
        when(policyReader.getPackage(PKG_KEY)).thenReturn(buildView());
        assertThatThrownBy(() -> resolver.confirmGuardian(PKG_KEY, STRANGER))
            .isInstanceOf(AccessDeniedException.class)
            .hasMessageContaining("guardian");
    }

    @Test
    @DisplayName("confirmBeneficiary returns PackageView when caller is beneficiary")
    void confirmBeneficiary_success() {
        when(policyReader.getPackage(PKG_KEY)).thenReturn(buildView());
        PackageView result = resolver.confirmBeneficiary(PKG_KEY, BENEFICIARY);
        assertThat(result.beneficiaryAddress()).isEqualTo(BENEFICIARY);
    }

    @Test
    @DisplayName("confirmBeneficiary throws for non-beneficiary")
    void confirmBeneficiary_nonBeneficiary_throws() {
        when(policyReader.getPackage(PKG_KEY)).thenReturn(buildView());
        assertThatThrownBy(() -> resolver.confirmBeneficiary(PKG_KEY, STRANGER))
            .isInstanceOf(AccessDeniedException.class)
            .hasMessageContaining("beneficiary");
    }

    @Test
    @DisplayName("confirmOwner throws when ownerAddress is null (DRAFT package)")
    void confirmOwner_nullOwner_throws() {
        PackageView draft = PackageView.draft();
        when(policyReader.getPackage(PKG_KEY)).thenReturn(draft);
        assertThatThrownBy(() -> resolver.confirmOwner(PKG_KEY, OWNER))
            .isInstanceOf(AccessDeniedException.class);
    }
}
