package com.arcadigitalis.backend.policy;

import com.arcadigitalis.backend.api.exception.ValidationException;
import com.arcadigitalis.backend.evm.Web3jConfig;
import org.springframework.stereotype.Component;

/**
 * Guards against ETH-bearing transactions when funding is disabled.
 * If {@code fundingEnabled=false} and {@code requestEthValue > 0},
 * throws ValidationException(400).
 */
@Component
public class FundingGuard {

    private final Web3jConfig config;

    public FundingGuard(Web3jConfig config) {
        this.config = config;
    }

    /**
     * Asserts that the instance allows ETH-bearing transactions.
     * Call before any endpoint that may carry ETH value (activate, renew).
     *
     * @param requestEthValue the ETH value in the request; null or "0x0" means no ETH
     * @throws ValidationException if funding is disabled and ETH value is non-zero
     */
    public void assertFundingAllowed(String requestEthValue) {
        if (!config.isFundingEnabled() && isNonZeroEthValue(requestEthValue)) {
            throw new ValidationException(
                "FundingDisabled: this instance does not accept ETH-bearing transactions"
            );
        }
    }

    private static boolean isNonZeroEthValue(String ethValue) {
        if (ethValue == null || ethValue.isBlank()) return false;
        String trimmed = ethValue.trim().toLowerCase();
        if (trimmed.equals("0x0") || trimmed.equals("0x00") || trimmed.equals("0")) return false;
        try {
            return !new java.math.BigInteger(trimmed.startsWith("0x") ? trimmed.substring(2) : trimmed, 16)
                .equals(java.math.BigInteger.ZERO);
        } catch (NumberFormatException e) {
            return false;
        }
    }
}
