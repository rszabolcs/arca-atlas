package com.arcadigitalis.backend.lit;

import com.arcadigitalis.backend.evm.PolicyReader;
import com.arcadigitalis.backend.evm.PolicyReader.PackageView;
import com.arcadigitalis.backend.evm.Web3jConfig;
import com.arcadigitalis.backend.lit.ManifestValidator.ValidationResult;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * Unit tests for ManifestValidator — T074.
 * Tests: Layer 1 structural, Layer 2 on-chain, Layer 3 blob guard.
 */
@ExtendWith(MockitoExtension.class)
class ManifestValidatorTest {

    @Mock private Web3jConfig config;
    @Mock private PolicyReader policyReader;

    private ManifestValidator validator;
    private final ObjectMapper mapper = new ObjectMapper();

    private static final String PKG_KEY = "0x" + "ab".repeat(32);
    private static final String PROXY = "0x1234567890abcdef1234567890abcdef12345678";
    private static final long CHAIN_ID = 11155111L;
    private static final String BENEFICIARY = "0x3333333333333333333333333333333333333333";

    @BeforeEach
    void setUp() {
        validator = new ManifestValidator(config, policyReader);
        when(config.getProxyAddress()).thenReturn(PROXY);
        when(config.getChainId()).thenReturn(CHAIN_ID);
    }

    private ObjectNode buildValidManifest() {
        ObjectNode manifest = mapper.createObjectNode();
        manifest.put("packageKey", PKG_KEY);

        ObjectNode policy = manifest.putObject("policy");
        policy.put("chainId", CHAIN_ID);
        policy.put("contract", PROXY);

        ObjectNode keyRelease = manifest.putObject("keyRelease");
        ObjectNode acc = keyRelease.putObject("accessControl");
        acc.put("functionName", "isReleased");
        keyRelease.put("requester", BENEFICIARY);
        keyRelease.put("encryptedSymmetricKey", "a]x1mKP9&2sZ$7gF!nT#vR@_wL5dE8qU");

        return manifest;
    }

    @Test
    @DisplayName("Valid manifest passes all three layers")
    void validManifest_passes() {
        when(policyReader.getPackageStatus(eq(PKG_KEY))).thenReturn("ACTIVE");
        when(policyReader.getPackage(eq(PKG_KEY))).thenReturn(new PackageView(
            "ACTIVE", "0xOwner", BENEFICIARY, "ipfs://Qm",
            List.of(), 0, 0, 0, 0, 0, 0, null, null, null, null
        ));

        ValidationResult result = validator.validate(buildValidManifest());

        assertThat(result.valid()).isTrue();
        assertThat(result.errors()).isEmpty();
    }

    @Test
    @DisplayName("Missing required fields fails Layer 1")
    void missingFields_failsLayer1() {
        ObjectNode manifest = mapper.createObjectNode();
        manifest.put("packageKey", PKG_KEY);
        // Missing policy and keyRelease

        ValidationResult result = validator.validate(manifest);

        assertThat(result.valid()).isFalse();
        assertThat(result.checks().get("structuralFields")).isFalse();
    }

    @Test
    @DisplayName("Wrong proxy address fails Layer 1")
    void wrongProxy_failsLayer1() {
        ObjectNode manifest = buildValidManifest();
        manifest.with("policy").put("contract", "0xwrongaddress");

        when(policyReader.getPackageStatus(eq(PKG_KEY))).thenReturn("ACTIVE");
        when(policyReader.getPackage(eq(PKG_KEY))).thenReturn(new PackageView(
            "ACTIVE", "0xOwner", BENEFICIARY, "ipfs://Qm",
            List.of(), 0, 0, 0, 0, 0, 0, null, null, null, null
        ));

        ValidationResult result = validator.validate(manifest);

        assertThat(result.valid()).isFalse();
        assertThat(result.checks().get("proxyAddressMatch")).isFalse();
    }

    @Test
    @DisplayName("Wrong chainId fails Layer 1")
    void wrongChainId_failsLayer1() {
        ObjectNode manifest = buildValidManifest();
        manifest.with("policy").put("chainId", 999);

        ValidationResult result = validator.validate(manifest);

        assertThat(result.valid()).isFalse();
        assertThat(result.checks().get("chainIdMatch")).isFalse();
    }

    @Test
    @DisplayName("Wrong functionName fails Layer 1")
    void wrongFunctionName_failsLayer1() {
        ObjectNode manifest = buildValidManifest();
        manifest.with("keyRelease").with("accessControl").put("functionName", "wrongFunc");

        ValidationResult result = validator.validate(manifest);

        assertThat(result.valid()).isFalse();
        assertThat(result.checks().get("functionNameMatch")).isFalse();
    }

    @Test
    @DisplayName("DRAFT package fails Layer 2")
    void draftPackage_failsLayer2() {
        when(policyReader.getPackageStatus(eq(PKG_KEY))).thenReturn("DRAFT");

        ValidationResult result = validator.validate(buildValidManifest());

        assertThat(result.valid()).isFalse();
        assertThat(result.checks().get("packageActivated")).isFalse();
    }

    @Test
    @DisplayName("Requester != beneficiary fails Layer 2")
    void wrongBeneficiary_failsLayer2() {
        when(policyReader.getPackageStatus(eq(PKG_KEY))).thenReturn("ACTIVE");
        when(policyReader.getPackage(eq(PKG_KEY))).thenReturn(new PackageView(
            "ACTIVE", "0xOwner", "0xDifferentBeneficiary", "ipfs://Qm",
            List.of(), 0, 0, 0, 0, 0, 0, null, null, null, null
        ));

        ValidationResult result = validator.validate(buildValidManifest());

        assertThat(result.valid()).isFalse();
        assertThat(result.checks().get("onChainBeneficiary")).isFalse();
    }

    @Test
    @DisplayName("Short encDek fails Layer 3 blob guard")
    void shortEncDek_failsLayer3() {
        ObjectNode manifest = buildValidManifest();
        manifest.with("keyRelease").put("encryptedSymmetricKey", "short");

        when(policyReader.getPackageStatus(eq(PKG_KEY))).thenReturn("ACTIVE");
        when(policyReader.getPackage(eq(PKG_KEY))).thenReturn(new PackageView(
            "ACTIVE", "0xOwner", BENEFICIARY, "ipfs://Qm",
            List.of(), 0, 0, 0, 0, 0, 0, null, null, null, null
        ));

        ValidationResult result = validator.validate(manifest);

        assertThat(result.valid()).isFalse();
    }
}
