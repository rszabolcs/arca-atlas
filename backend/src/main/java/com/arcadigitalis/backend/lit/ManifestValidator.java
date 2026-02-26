package com.arcadigitalis.backend.lit;

import com.arcadigitalis.backend.api.exception.ValidationException;
import com.arcadigitalis.backend.evm.PolicyReader;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class ManifestValidator {

    private final PolicyReader policyReader;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${arca.evm.policy-proxy-address}")
    private String expectedProxyAddress;

    @Value("${arca.evm.chain-id}")
    private Long expectedChainId;

    public ManifestValidator(PolicyReader policyReader) {
        this.policyReader = policyReader;
    }

    public void validate(String manifestJson) {
        try {
            JsonNode manifest = objectMapper.readTree(manifestJson);

            // Layer 1: Local structural validation
            validateStructure(manifest);

            // Layer 2: Live RPC validation
            validateLiveRpc(manifest);

            // Layer 3: Blob guard
            validateBlobGuard(manifest);

        } catch (Exception e) {
            throw new ValidationException("Manifest validation failed: " + e.getMessage());
        }
    }

    private void validateStructure(JsonNode manifest) {
        // Required fields
        String packageKey = getRequiredField(manifest, "packageKey");
        JsonNode policy = getRequiredNode(manifest, "policy");
        Long chainId = getRequiredField(policy, "chainId").asLong();
        String contractAddress = getRequiredField(policy, "contract");

        JsonNode keyRelease = getRequiredNode(manifest, "keyRelease");
        JsonNode accessControl = getRequiredNode(keyRelease, "accessControl");
        String requester = getRequiredField(keyRelease, "requester");
        String encryptedSymmetricKey = getRequiredField(keyRelease, "encryptedSymmetricKey");

        // Validate proxy address match
        if (!expectedProxyAddress.equalsIgnoreCase(contractAddress)) {
            throw new ValidationException("Layer 1 failed: contractAddress does not match configured proxy");
        }

        // Validate chain ID match
        if (!expectedChainId.equals(chainId)) {
            throw new ValidationException("Layer 1 failed: chainId does not match configured chain");
        }

        // Validate ACC structure
        String functionName = getRequiredField(accessControl, "functionName");
        if (!"isReleased".equals(functionName)) {
            throw new ValidationException("Layer 1 failed: functionName must be 'isReleased'");
        }

        JsonNode functionParams = getRequiredNode(accessControl, "functionParams");
        if (!functionParams.isArray() || functionParams.size() == 0) {
            throw new ValidationException("Layer 1 failed: functionParams must contain packageKey");
        }

        String accPackageKey = functionParams.get(0).asText();
        if (!packageKey.equals(accPackageKey)) {
            throw new ValidationException("Layer 1 failed: functionParams[0] must match manifest packageKey");
        }
    }

    private void validateLiveRpc(JsonNode manifest) {
        String packageKey = manifest.get("packageKey").asText();
        String requester = manifest.get("keyRelease").get("requester").asText();

        // Check package status (must not be DRAFT)
        String status = policyReader.getPackageStatus(packageKey);
        if ("DRAFT".equals(status)) {
            throw new ValidationException("Layer 2 failed: package is in DRAFT status");
        }

        // Check beneficiary match
        PolicyReader.PackageView packageView = policyReader.getPackage(packageKey);
        if (!requester.equalsIgnoreCase(packageView.beneficiaryAddress())) {
            throw new ValidationException("Layer 2 failed: requester does not match on-chain beneficiary");
        }
    }

    private void validateBlobGuard(JsonNode manifest) {
        String encryptedSymmetricKey = manifest.get("keyRelease").get("encryptedSymmetricKey").asText();

        if (encryptedSymmetricKey == null || encryptedSymmetricKey.isBlank() || encryptedSymmetricKey.length() <= 10) {
            throw new ValidationException("Layer 3 failed: encryptedSymmetricKey is invalid or too short");
        }
    }

    private String getRequiredField(JsonNode node, String fieldName) {
        JsonNode field = node.get(fieldName);
        if (field == null || field.isNull()) {
            throw new ValidationException("Missing required field: " + fieldName);
        }
        return field.asText();
    }

    private JsonNode getRequiredNode(JsonNode node, String fieldName) {
        JsonNode field = node.get(fieldName);
        if (field == null || field.isNull()) {
            throw new ValidationException("Missing required field: " + fieldName);
        }
        return field;
    }
}
