package com.arcadigitalis.backend.evm;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.http.HttpService;

@Configuration
public class Web3jConfig {

    @Value("${arca.evm.rpc-url}")
    private String rpcUrl;

    @Value("${arca.evm.chain-id}")
    private Long chainId;

    @Value("${arca.evm.policy-proxy-address}")
    private String policyProxyAddress;

    @Bean
    public Web3j web3j() {
        return Web3j.build(new HttpService(rpcUrl));
    }

    @Bean
    public Long chainId() {
        return chainId;
    }

    @Bean
    public String policyProxyAddress() {
        return policyProxyAddress;
    }

    // TODO: T018 - Read pricePerSecond from contract at startup and expose fundingEnabled bean
    @Bean
    public boolean fundingEnabled() {
        // Placeholder: will be implemented in T018 to read from contract
        return false;
    }
}
