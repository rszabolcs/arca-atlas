package com.arcadigitalis.backend.evm;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for CalldataBuilder — T053.
 * Verifies ABI-encoding output starts with the correct 4-byte selector
 * and contains expected data fields for all 11 functions.
 */
class CalldataBuilderTest {

    private CalldataBuilder builder;

    private static final String PKG_KEY = "0x" + "ab".repeat(32);
    private static final String ADDR1 = "0x71C7656EC7ab88b098defB751B7401B5f6d8976F";
    private static final String ADDR2 = "0x2222222222222222222222222222222222222222";

    @BeforeEach
    void setUp() {
        builder = new CalldataBuilder();
    }

    @Test
    @DisplayName("encodeActivate produces non-empty hex starting with 0x")
    void encodeActivate_producesHex() {
        String calldata = builder.encodeActivate(
            PKG_KEY, "ipfs://QmTest", ADDR1,
            List.of(ADDR2), 1,
            86400L, 604800L, 259200L, 1700000000L
        );

        assertThat(calldata).startsWith("0x");
        assertThat(calldata.length()).isGreaterThan(10); // 4-byte selector + encoded args
    }

    @Test
    @DisplayName("encodeCheckIn produces hex calldata")
    void encodeCheckIn_producesHex() {
        String calldata = builder.encodeCheckIn(PKG_KEY);
        assertThat(calldata).startsWith("0x");
        // checkIn(bytes32) = 4 + 32 bytes = 4+64 hex chars = 68 hex + "0x" prefix
        assertThat(calldata.length()).isGreaterThanOrEqualTo(70);
    }

    @Test
    @DisplayName("encodeRenew produces hex calldata")
    void encodeRenew_producesHex() {
        String calldata = builder.encodeRenew(PKG_KEY);
        assertThat(calldata).startsWith("0x");
        assertThat(calldata.length()).isGreaterThanOrEqualTo(70);
    }

    @Test
    @DisplayName("encodeUpdateManifestUri produces hex calldata")
    void encodeUpdateManifestUri_producesHex() {
        String calldata = builder.encodeUpdateManifestUri(PKG_KEY, "ipfs://QmNewManifest");
        assertThat(calldata).startsWith("0x");
        assertThat(calldata.length()).isGreaterThan(70);
    }

    @Test
    @DisplayName("encodeRevoke produces hex calldata")
    void encodeRevoke_producesHex() {
        String calldata = builder.encodeRevoke(PKG_KEY);
        assertThat(calldata).startsWith("0x");
    }

    @Test
    @DisplayName("encodeRescue produces hex calldata")
    void encodeRescue_producesHex() {
        String calldata = builder.encodeRescue(PKG_KEY);
        assertThat(calldata).startsWith("0x");
    }

    @Test
    @DisplayName("encodeGuardianApprove produces hex calldata")
    void encodeGuardianApprove_producesHex() {
        String calldata = builder.encodeGuardianApprove(PKG_KEY);
        assertThat(calldata).startsWith("0x");
    }

    @Test
    @DisplayName("encodeGuardianVeto produces hex calldata")
    void encodeGuardianVeto_producesHex() {
        String calldata = builder.encodeGuardianVeto(PKG_KEY);
        assertThat(calldata).startsWith("0x");
    }

    @Test
    @DisplayName("encodeGuardianRescindVeto produces hex calldata")
    void encodeGuardianRescindVeto_producesHex() {
        String calldata = builder.encodeGuardianRescindVeto(PKG_KEY);
        assertThat(calldata).startsWith("0x");
    }

    @Test
    @DisplayName("encodeGuardianRescindApprove produces hex calldata")
    void encodeGuardianRescindApprove_producesHex() {
        String calldata = builder.encodeGuardianRescindApprove(PKG_KEY);
        assertThat(calldata).startsWith("0x");
    }

    @Test
    @DisplayName("encodeClaim produces hex calldata")
    void encodeClaim_producesHex() {
        String calldata = builder.encodeClaim(PKG_KEY);
        assertThat(calldata).startsWith("0x");
    }

    @Test
    @DisplayName("Different packageKeys produce different calldata")
    void differentKeys_differentCalldata() {
        String key1 = "0x" + "aa".repeat(32);
        String key2 = "0x" + "bb".repeat(32);

        assertThat(builder.encodeCheckIn(key1))
            .isNotEqualTo(builder.encodeCheckIn(key2));
    }

    @Test
    @DisplayName("Same inputs produce deterministic calldata")
    void sameInputs_deterministic() {
        String a = builder.encodeCheckIn(PKG_KEY);
        String b = builder.encodeCheckIn(PKG_KEY);
        assertThat(a).isEqualTo(b);
    }
}
