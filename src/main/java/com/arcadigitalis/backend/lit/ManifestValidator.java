package com.arcadigitalis.backend.lit;

import com.arcadigitalis.backend.api.exception.ValidationException;
import com.arcadigitalis.backend.evm.PolicyReader;
import com.arcadigitalis.backend.evm.Web3jConfig;
import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Three-layer manifest validation per FR-020:
 * <ul>
 *   <li><b>Layer 1 (local structural)</b> — required fields, proxy/chainId/function matching</li>
 *   <li><b>Layer 2 (live RPC)</b> — package status != DRAFT, on-chain beneficiary == requester</li>
 *   <li><b>Layer 3 (blob guard)</b> — encryptedSymmetricKey non-empty, length > 10</li>
 * </ul>
 * No Lit network calls.
 */
@Service
public class ManifestValidator {

    private static final Logger log = LoggerFactory.getLogger(ManifestValidator.class);

    private final Web3jConfig config;
    private final PolicyReader policyReader;

    public ManifestValidator(Web3jConfig config, PolicyReader policyReader) {
        this.config = config;
        this.policyReader = policyReader;
    }

    public ValidationResult validate(JsonNode manifest) {
        Map<String, Boolean> checks = new LinkedHashMap<>();
        List<String> errors = new ArrayList<>();

        // ── Layer 1: Local structural validation ──────────────────────────

        // Required fields present
        boolean structuralFields = hasRequiredFields(manifest);
        checks.put("structuralFields", structuralFields);
        if (!structuralFields) {
            errors.add("Missing required fields: packageKey, policy.chainId, policy.contract, keyRelease.accessControl, keyRelease.requester, keyRelease.encryptedSymmetricKey");
        }

        if (!structuralFields) {
            return new ValidationResult(false, checks, errors);
        }

        // Proxy address matches
        String manifestContract = manifest.at("/policy/contract").asText("");
        boolean proxyMatch = manifestContract.equalsIgnoreCase(config.getProxyAddress());
        checks.put("proxyAddressMatch", proxyMatch);
        if (!proxyMatch) {
            errors.add("contractAddress '" + manifestContract + "' does not match configured proxy '" + config.getProxyAddress() + "'");
        }

        // Chain ID matches
        long manifestChainId = manifest.at("/policy/chainId").asLong(0);
        boolean chainIdMatch = manifestChainId == config.getChainId();
        checks.put("chainIdMatch", chainIdMatch);
        if (!chainIdMatch) {
            errors.add("chainId " + manifestChainId + " does not match configured chainId " + config.getChainId());
        }

        // Package key present
        String packageKey = manifest.at("/packageKey").asText("");
        boolean packageKeyMatch = !packageKey.isBlank();
        checks.put("packageKeyMatch", packageKeyMatch);

        // Function name check on ACC
        JsonNode accessControl = manifest.at("/keyRelease/accessControl");
        String functionName = accessControl.has("functionName") ? accessControl.get("functionName").asText("") : "";
        boolean functionNameMatch = "isReleased".equals(functionName);
        checks.put("functionNameMatch", functionNameMatch);
        if (!functionNameMatch) {
            errors.add("functionName must be 'isReleased', got '" + functionName + "'");
        }

        // Requester == requester in keyRelease
        String requester = manifest.at("/keyRelease/requester").asText("");
        boolean requesterMatch = !requester.isBlank();
        checks.put("requesterMatch", requesterMatch);

        // encDEK present (layer 1 basic check)
        String encDek = manifest.at("/keyRelease/encryptedSymmetricKey").asText("");
        boolean encDekPresent = !encDek.isBlank();
        checks.put("encDekPresent", encDekPresent);
        if (!encDekPresent) {
            errors.add("encryptedSymmetricKey is empty or missing");
        }

        if (!errors.isEmpty()) {
            return new ValidationResult(false, checks, errors);
        }

        // ── Layer 2: Live RPC validation ──────────────────────────────────

        // Package must exist on chain (not DRAFT)
        String status = policyReader.getPackageStatus(packageKey);
        boolean packageActivated = !"DRAFT".equals(status);
        checks.put("packageActivated", packageActivated);
        if (!packageActivated) {
            errors.add("Package " + packageKey + " has status DRAFT — not activated on chain");
        }

        // On-chain beneficiary must match manifest requester
        boolean onChainBeneficiary = false;
        if (packageActivated) {
            PolicyReader.PackageView view = policyReader.getPackage(packageKey);
            onChainBeneficiary = requester.equalsIgnoreCase(view.beneficiaryAddress());
            if (!onChainBeneficiary) {
                errors.add("On-chain beneficiary '" + view.beneficiaryAddress() + "' does not match manifest requester '" + requester + "'");
            }
        }
        checks.put("onChainBeneficiary", onChainBeneficiary);

        if (!errors.isEmpty()) {
            return new ValidationResult(false, checks, errors);
        }

        // ── Layer 3: Blob guard ───────────────────────────────────────────

        boolean blobGuard = encDek.trim().length() > 10;
        if (!blobGuard) {
            errors.add("encryptedSymmetricKey is too short (must be > 10 characters)");
            checks.put("encDekPresent", false);
            return new ValidationResult(false, checks, errors);
        }

        return new ValidationResult(true, checks, errors);
    }

    private boolean hasRequiredFields(JsonNode manifest) {
        return manifest.has("packageKey")
            && !manifest.at("/policy/chainId").isMissingNode()
            && !manifest.at("/policy/contract").isMissingNode()
            && !manifest.at("/keyRelease/accessControl").isMissingNode()
            && !manifest.at("/keyRelease/requester").isMissingNode()
            && !manifest.at("/keyRelease/encryptedSymmetricKey").isMissingNode();
    }

    public record ValidationResult(boolean valid, Map<String, Boolean> checks, List<String> errors) {}
}
