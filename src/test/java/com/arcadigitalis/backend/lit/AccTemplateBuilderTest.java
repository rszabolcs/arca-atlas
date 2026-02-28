package com.arcadigitalis.backend.lit;

import com.arcadigitalis.backend.evm.Web3jConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * Unit tests for AccTemplateBuilder — T074.
 * Verifies ACC JSON template structure.
 */
@ExtendWith(MockitoExtension.class)
class AccTemplateBuilderTest {

    @Mock private Web3jConfig config;

    private AccTemplateBuilder builder;

    private static final long CHAIN_ID = 11155111L;
    private static final String PROXY = "0x1234567890abcdef1234567890abcdef12345678";

    @BeforeEach
    void setUp() {
        builder = new AccTemplateBuilder(new ChainNameRegistry());
    }

    @Test
    @DisplayName("buildEvmContractAcc produces valid JSON containing contractAddress")
    void buildAcc_containsContractAddress() {
        String acc = builder.buildEvmContractAcc(CHAIN_ID, PROXY, "0xPackageKey", "0xBeneficiary");
        assertThat(acc).contains(PROXY.toLowerCase());
    }

    @Test
    @DisplayName("buildEvmContractAcc includes isReleased function")
    void buildAcc_containsFunction() {
        String acc = builder.buildEvmContractAcc(CHAIN_ID, PROXY, "0xPackageKey", "0xBeneficiary");
        assertThat(acc).contains("isReleased");
    }

    @Test
    @DisplayName("buildEvmContractAcc includes packageKey and beneficiary")
    void buildAcc_containsPackageKeyAndBeneficiary() {
        String acc = builder.buildEvmContractAcc(CHAIN_ID, PROXY, "0xMyKey", "0xMyBeneficiary");
        assertThat(acc).contains("0xMyKey");
        assertThat(acc).contains("0xMyBeneficiary");
    }

    @Test
    @DisplayName("buildEvmContractAcc produces valid JSON")
    void buildAcc_validJson() {
        String acc = builder.buildEvmContractAcc(CHAIN_ID, PROXY, "0xKey", "0xBen");
        assertThat(acc).startsWith("{").endsWith("}");
    }
}
