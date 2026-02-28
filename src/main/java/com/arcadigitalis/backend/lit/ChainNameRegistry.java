package com.arcadigitalis.backend.lit;

import com.arcadigitalis.backend.api.exception.ValidationException;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Maps EVM chain IDs to Lit Protocol chain name strings.
 * Extensible via configuration in future phases.
 */
@Component
public class ChainNameRegistry {

    private static final Map<Long, String> CHAIN_NAMES = Map.ofEntries(
        Map.entry(1L, "ethereum"),
        Map.entry(5L, "goerli"),
        Map.entry(10L, "optimism"),
        Map.entry(137L, "polygon"),
        Map.entry(42161L, "arbitrum"),
        Map.entry(8453L, "base"),
        Map.entry(11155111L, "sepolia"),
        Map.entry(80001L, "mumbai"),
        Map.entry(84532L, "baseSepolia"),
        Map.entry(421614L, "arbitrumSepolia"),
        Map.entry(11155420L, "optimismSepolia")
    );

    /**
     * Returns the Lit Protocol chain name for the given chain ID.
     *
     * @throws ValidationException if chainId is not mapped
     */
    public String getChainName(long chainId) {
        String name = CHAIN_NAMES.get(chainId);
        if (name == null) {
            throw new ValidationException("Unknown chain ID for Lit Protocol: " + chainId);
        }
        return name;
    }
}
