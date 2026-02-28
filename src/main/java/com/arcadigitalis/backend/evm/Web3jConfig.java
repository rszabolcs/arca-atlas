package com.arcadigitalis.backend.evm;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.http.HttpService;

import java.math.BigInteger;

@Configuration
public class Web3jConfig {

    private static final Logger log = LoggerFactory.getLogger(Web3jConfig.class);

    @Value("${arca.evm.rpc-url}")
    private String rpcUrl;

    @Value("${arca.evm.chain-id}")
    private long chainId;

    @Value("${arca.policy.proxy-address}")
    private String proxyAddress;

    private boolean fundingEnabled = false;

    @Bean
    public Web3j web3j() {
        log.info("Connecting to EVM RPC: {}", rpcUrl);
        return Web3j.build(new HttpService(rpcUrl));
    }

    @PostConstruct
    public void validateConfiguration() {
        if (proxyAddress == null || proxyAddress.isBlank() ||
            proxyAddress.equals("0x0000000000000000000000000000000000000000")) {
            log.warn("ARCA_POLICY_PROXY_ADDRESS is not configured or is the zero address");
        }
        if (chainId <= 0) {
            throw new IllegalStateException("ARCA_EVM_CHAIN_ID must be a positive integer");
        }
        log.info("Arca Backend configured for chain={} proxy={}", chainId, proxyAddress);
    }

    public long getChainId() { return chainId; }
    public String getProxyAddress() { return proxyAddress; }
    public boolean isFundingEnabled() { return fundingEnabled; }
    public void setFundingEnabled(boolean fundingEnabled) { this.fundingEnabled = fundingEnabled; }
}
