package com.arcadigitalis.backend.policy;

import com.arcadigitalis.backend.api.dto.PackageStatusResponse;
import com.arcadigitalis.backend.evm.PolicyReader;
import com.arcadigitalis.backend.evm.PolicyReader.PackageView;
import com.arcadigitalis.backend.evm.Web3jConfig;
import com.arcadigitalis.backend.lit.AccTemplateBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * Unit tests for PackageService — T038.
 *
 * Tests: DRAFT for unknown key; all 7 status values; WARNING not collapsed
 * to ACTIVE; CLAIMABLE not collapsed to PENDING_RELEASE; live chain override.
 */
@ExtendWith(MockitoExtension.class)
class PackageServiceTest {

    @Mock private PolicyReader policyReader;
    @Mock private Web3jConfig config;
    @Mock private AccTemplateBuilder accTemplateBuilder;

    private PackageService packageService;

    private static final String PKG_KEY = "0x" + "ab".repeat(32);
    private static final long CHAIN_ID = 11155111L;
    private static final String PROXY = "0x1234567890abcdef1234567890abcdef12345678";

    @BeforeEach
    void setUp() {
        packageService = new PackageService(policyReader, config, accTemplateBuilder);
        when(config.getChainId()).thenReturn(CHAIN_ID);
        when(config.getProxyAddress()).thenReturn(PROXY);
    }

    @Test
    @DisplayName("Unknown package key returns DRAFT status (FR-009)")
    void unknownPackage_returnsDraft() {
        when(policyReader.getPackage(eq(PKG_KEY))).thenReturn(PackageView.draft());

        PackageStatusResponse response = packageService.getPackageView(PKG_KEY);

        assertThat(response.status()).isEqualTo("DRAFT");
        assertThat(response.ownerAddress()).isNull();
        assertThat(response.liveRead()).isTrue();
    }

    @Test
    @DisplayName("ACTIVE status round-trips correctly")
    void activePackage_roundTrips() {
        PackageView view = buildView("ACTIVE");
        when(policyReader.getPackage(eq(PKG_KEY))).thenReturn(view);

        PackageStatusResponse response = packageService.getPackageView(PKG_KEY);

        assertThat(response.status()).isEqualTo("ACTIVE");
        assertThat(response.ownerAddress()).isEqualTo("0xOwner");
        assertThat(response.beneficiaryAddress()).isEqualTo("0xBeneficiary");
        assertThat(response.guardians()).containsExactly("0xGuardian1");
        assertThat(response.guardianQuorum()).isEqualTo(1);
    }

    @Test
    @DisplayName("WARNING is surfaced as-is, never collapsed to ACTIVE (FR-009a)")
    void warningStatus_notCollapsed() {
        PackageView view = buildView("WARNING");
        when(policyReader.getPackage(eq(PKG_KEY))).thenReturn(view);

        PackageStatusResponse response = packageService.getPackageView(PKG_KEY);

        assertThat(response.status()).isEqualTo("WARNING");
    }

    @Test
    @DisplayName("CLAIMABLE is surfaced as-is, never collapsed to PENDING_RELEASE (FR-009a)")
    void claimableStatus_notCollapsed() {
        PackageView view = buildView("CLAIMABLE");
        when(policyReader.getPackage(eq(PKG_KEY))).thenReturn(view);

        PackageStatusResponse response = packageService.getPackageView(PKG_KEY);

        assertThat(response.status()).isEqualTo("CLAIMABLE");
    }

    @Test
    @DisplayName("PENDING_RELEASE round-trips correctly")
    void pendingRelease_roundTrips() {
        PackageView view = buildView("PENDING_RELEASE");
        when(policyReader.getPackage(eq(PKG_KEY))).thenReturn(view);

        assertThat(packageService.getPackageView(PKG_KEY).status()).isEqualTo("PENDING_RELEASE");
    }

    @Test
    @DisplayName("RELEASED round-trips correctly")
    void released_roundTrips() {
        PackageView view = buildView("RELEASED");
        when(policyReader.getPackage(eq(PKG_KEY))).thenReturn(view);

        assertThat(packageService.getPackageView(PKG_KEY).status()).isEqualTo("RELEASED");
    }

    @Test
    @DisplayName("REVOKED round-trips correctly")
    void revoked_roundTrips() {
        PackageView view = buildView("REVOKED");
        when(policyReader.getPackage(eq(PKG_KEY))).thenReturn(view);

        assertThat(packageService.getPackageView(PKG_KEY).status()).isEqualTo("REVOKED");
    }

    @Test
    @DisplayName("All PackageView fields map to response DTO")
    void allFieldsMapped() {
        Instant now = Instant.now();
        PackageView view = new PackageView(
            "ACTIVE", "0xOwner", "0xBeneficiary", "ipfs://QmManifest",
            List.of("0xG1", "0xG2"), 2,
            1, 0,
            86400L, 604800L, 259200,
            now, now.plusSeconds(86400),
            null, null
        );
        when(policyReader.getPackage(eq(PKG_KEY))).thenReturn(view);

        PackageStatusResponse r = packageService.getPackageView(PKG_KEY);

        assertThat(r.chainId()).isEqualTo(CHAIN_ID);
        assertThat(r.proxyAddress()).isEqualTo(PROXY);
        assertThat(r.packageKey()).isEqualTo(PKG_KEY);
        assertThat(r.status()).isEqualTo("ACTIVE");
        assertThat(r.ownerAddress()).isEqualTo("0xOwner");
        assertThat(r.beneficiaryAddress()).isEqualTo("0xBeneficiary");
        assertThat(r.manifestUri()).isEqualTo("ipfs://QmManifest");
        assertThat(r.guardians()).containsExactly("0xG1", "0xG2");
        assertThat(r.guardianQuorum()).isEqualTo(2);
        assertThat(r.vetoCount()).isEqualTo(1);
        assertThat(r.approvalCount()).isEqualTo(0);
        assertThat(r.warnThreshold()).isEqualTo(86400L);
        assertThat(r.inactivityThreshold()).isEqualTo(604800L);
        assertThat(r.gracePeriodSeconds()).isEqualTo(259200);
        assertThat(r.lastCheckIn()).isEqualTo(now);
        assertThat(r.paidUntil()).isEqualTo(now.plusSeconds(86400));
        assertThat(r.pendingSince()).isNull();
        assertThat(r.releasedAt()).isNull();
        assertThat(r.liveRead()).isTrue();
    }

    // ── Helper ─────────────────────────────────────────────────────────────

    private PackageView buildView(String status) {
        return new PackageView(
            status, "0xOwner", "0xBeneficiary", "ipfs://Qm",
            List.of("0xGuardian1"), 1,
            0, 0,
            86400L, 604800L, 259200,
            null, null, null, null
        );
    }
}
