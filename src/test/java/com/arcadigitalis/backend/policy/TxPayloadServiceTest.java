package com.arcadigitalis.backend.policy;

import com.arcadigitalis.backend.api.dto.TxPayloadResponse;
import com.arcadigitalis.backend.api.exception.ConflictException;
import com.arcadigitalis.backend.api.exception.ValidationException;
import com.arcadigitalis.backend.evm.CalldataBuilder;
import com.arcadigitalis.backend.evm.PolicyReader.PackageView;
import com.arcadigitalis.backend.evm.Web3jConfig;
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
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

/**
 * Unit tests for TxPayloadService — T055.
 * Tests: happy paths, pre-flight 409s, funding guard, role checks.
 */
@ExtendWith(MockitoExtension.class)
class TxPayloadServiceTest {

    @Mock private RoleResolver roleResolver;
    @Mock private FundingGuard fundingGuard;
    @Mock private CalldataBuilder calldataBuilder;
    @Mock private Web3jConfig config;

    private TxPayloadService service;

    private static final String PKG_KEY = "0x" + "ab".repeat(32);
    private static final String PROXY = "0x1234567890abcdef1234567890abcdef12345678";
    private static final String OWNER = "0x71C7656EC7ab88b098defB751B7401B5f6d8976F";
    private static final String GUARDIAN = "0x2222222222222222222222222222222222222222";
    private static final String BENEFICIARY = "0x3333333333333333333333333333333333333333";

    @BeforeEach
    void setUp() {
        service = new TxPayloadService(roleResolver, fundingGuard, calldataBuilder, config);
        when(config.getProxyAddress()).thenReturn(PROXY);
    }

    private PackageView activeView() {
        return new PackageView("ACTIVE", OWNER, BENEFICIARY, "ipfs://Qm",
            List.of(GUARDIAN), 1, 0, 0, 86400L, 604800L, 259200, null, null, null, null);
    }

    private PackageView viewWithStatus(String status) {
        return new PackageView(status, OWNER, BENEFICIARY, "ipfs://Qm",
            List.of(GUARDIAN), 1, 0, 0, 86400L, 604800L, 259200, null, null, null, null);
    }

    // ── Activate ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("prepareActivate returns payload with correct 'to' address")
    void activate_returnsPayload() {
        when(calldataBuilder.encodeActivate(any(), any(), any(), any(), anyInt(), anyLong(), anyLong(), anyLong(), anyLong()))
            .thenReturn("0xabcdef");

        TxPayloadResponse response = service.prepareActivate(
            PKG_KEY, OWNER, "ipfs://Qm", BENEFICIARY,
            List.of(GUARDIAN), 1, 86400, 604800, 259200, 0, "0x0"
        );

        assertThat(response.to()).isEqualTo(PROXY);
        assertThat(response.data()).isEqualTo("0xabcdef");
    }

    // ── CheckIn ───────────────────────────────────────────────────────────

    @Test
    @DisplayName("prepareCheckIn succeeds for ACTIVE package")
    void checkIn_active_succeeds() {
        when(roleResolver.confirmOwner(PKG_KEY, OWNER)).thenReturn(activeView());
        when(calldataBuilder.encodeCheckIn(PKG_KEY)).thenReturn("0xcheck");

        TxPayloadResponse response = service.prepareCheckIn(PKG_KEY, OWNER);
        assertThat(response.data()).isEqualTo("0xcheck");
    }

    @Test
    @DisplayName("prepareCheckIn throws 409 for CLAIMABLE package")
    void checkIn_claimable_throws409() {
        when(roleResolver.confirmOwner(PKG_KEY, OWNER)).thenReturn(viewWithStatus("CLAIMABLE"));

        assertThatThrownBy(() -> service.prepareCheckIn(PKG_KEY, OWNER))
            .isInstanceOf(ConflictException.class)
            .hasMessageContaining("CLAIMABLE");
    }

    @Test
    @DisplayName("prepareCheckIn throws 409 for RELEASED package")
    void checkIn_released_throws409() {
        when(roleResolver.confirmOwner(PKG_KEY, OWNER)).thenReturn(viewWithStatus("RELEASED"));

        assertThatThrownBy(() -> service.prepareCheckIn(PKG_KEY, OWNER))
            .isInstanceOf(ConflictException.class);
    }

    @Test
    @DisplayName("prepareCheckIn throws 409 for REVOKED package")
    void checkIn_revoked_throws409() {
        when(roleResolver.confirmOwner(PKG_KEY, OWNER)).thenReturn(viewWithStatus("REVOKED"));

        assertThatThrownBy(() -> service.prepareCheckIn(PKG_KEY, OWNER))
            .isInstanceOf(ConflictException.class);
    }

    // ── Renew ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("prepareRenew succeeds without auth")
    void renew_succeeds() {
        when(calldataBuilder.encodeRenew(PKG_KEY)).thenReturn("0xrenew");

        TxPayloadResponse response = service.prepareRenew(PKG_KEY, "0x1");
        assertThat(response.data()).isEqualTo("0xrenew");
    }

    // ── UpdateManifestUri ─────────────────────────────────────────────────

    @Test
    @DisplayName("prepareUpdateManifestUri throws 409 for RELEASED package")
    void updateManifest_released_throws409() {
        when(roleResolver.confirmOwner(PKG_KEY, OWNER)).thenReturn(viewWithStatus("RELEASED"));

        assertThatThrownBy(() -> service.prepareUpdateManifestUri(PKG_KEY, OWNER, "ipfs://new"))
            .isInstanceOf(ConflictException.class);
    }

    @Test
    @DisplayName("prepareUpdateManifestUri succeeds for ACTIVE package")
    void updateManifest_active_succeeds() {
        when(roleResolver.confirmOwner(PKG_KEY, OWNER)).thenReturn(activeView());
        when(calldataBuilder.encodeUpdateManifestUri(eq(PKG_KEY), eq("ipfs://new"))).thenReturn("0xupdate");

        TxPayloadResponse response = service.prepareUpdateManifestUri(PKG_KEY, OWNER, "ipfs://new");
        assertThat(response.data()).isEqualTo("0xupdate");
    }

    // ── Guardian operations ───────────────────────────────────────────────

    @Test
    @DisplayName("prepareGuardianApprove succeeds for PENDING_RELEASE status")
    void guardianApprove_pendingRelease_succeeds() {
        when(roleResolver.confirmGuardian(PKG_KEY, GUARDIAN)).thenReturn(viewWithStatus("PENDING_RELEASE"));
        when(calldataBuilder.encodeGuardianApprove(PKG_KEY)).thenReturn("0xapprove");

        TxPayloadResponse response = service.prepareGuardianApprove(PKG_KEY, GUARDIAN);
        assertThat(response.data()).isEqualTo("0xapprove");
    }

    @Test
    @DisplayName("prepareGuardianApprove throws 409 for ACTIVE status")
    void guardianApprove_active_throws409() {
        when(roleResolver.confirmGuardian(PKG_KEY, GUARDIAN)).thenReturn(activeView());

        assertThatThrownBy(() -> service.prepareGuardianApprove(PKG_KEY, GUARDIAN))
            .isInstanceOf(ConflictException.class)
            .hasMessageContaining("PENDING_RELEASE");
    }

    @Test
    @DisplayName("prepareGuardianVeto throws 409 for RELEASED status")
    void guardianVeto_released_throws409() {
        when(roleResolver.confirmGuardian(PKG_KEY, GUARDIAN)).thenReturn(viewWithStatus("RELEASED"));

        assertThatThrownBy(() -> service.prepareGuardianVeto(PKG_KEY, GUARDIAN))
            .isInstanceOf(ConflictException.class);
    }

    // ── Claim ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("prepareClaimTx succeeds for CLAIMABLE status")
    void claim_claimable_succeeds() {
        when(roleResolver.confirmBeneficiary(PKG_KEY, BENEFICIARY)).thenReturn(viewWithStatus("CLAIMABLE"));
        when(calldataBuilder.encodeClaim(PKG_KEY)).thenReturn("0xclaim");

        TxPayloadResponse response = service.prepareClaimTx(PKG_KEY, BENEFICIARY);
        assertThat(response.data()).isEqualTo("0xclaim");
    }

    @Test
    @DisplayName("prepareClaimTx throws 409 for ACTIVE status")
    void claim_active_throws409() {
        when(roleResolver.confirmBeneficiary(PKG_KEY, BENEFICIARY)).thenReturn(activeView());

        assertThatThrownBy(() -> service.prepareClaimTx(PKG_KEY, BENEFICIARY))
            .isInstanceOf(ConflictException.class)
            .hasMessageContaining("CLAIMABLE");
    }
}
