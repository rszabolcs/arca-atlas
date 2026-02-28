package com.arcadigitalis.backend.policy;

import com.arcadigitalis.backend.api.exception.ValidationException;
import com.arcadigitalis.backend.evm.Web3jConfig;
import org.springframework.stereotype.Component;

/**
 * Guards against requests specifying a proxyAddress or chainId
 * that does not match the instance's configured values (NFR-007).
 */
@Component
public class ConfigGuard {

    private final Web3jConfig config;

    public ConfigGuard(Web3jConfig config) {
        this.config = config;
    }

    /**
     * Validates that the provided chainId and proxyAddress match the configured values.
     * Call at controller entry for any endpoint that accepts these parameters.
     *
     * @throws ValidationException on mismatch
     */
    public void assertMatchesConfig(long chainId, String proxyAddress) {
        if (chainId != config.getChainId()) {
            throw new ValidationException(
                "chainId " + chainId + " does not match configured chainId " + config.getChainId()
            );
        }
        if (proxyAddress != null && !proxyAddress.equalsIgnoreCase(config.getProxyAddress())) {
            throw new ValidationException(
                "proxyAddress does not match configured proxy"
            );
        }
    }
}
