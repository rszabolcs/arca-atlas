package com.arcadigitalis.backend.lit;

import com.arcadigitalis.backend.api.exception.ValidationException;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class ChainNameRegistry {

    private static final Map<Long, String> CHAIN_ID_TO_LIT_NAME = Map.of(
        1L, "ethereum",
        11155111L, "sepolia",
        137L, "polygon",
        80001L, "mumbai",
        56L, "bsc",
        97L, "bscTestnet",
        43114L, "avalanche",
        43113L, "avalancheFuji"
    );

    public String getChainName(Long chainId) {
        String chainName = CHAIN_ID_TO_LIT_NAME.get(chainId);
        if (chainName == null) {
            throw new ValidationException("Unknown chain ID for Lit Protocol: " + chainId);
        }
        return chainName;
    }
}
