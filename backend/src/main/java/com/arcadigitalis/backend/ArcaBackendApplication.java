package com.arcadigitalis.backend;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@ConfigurationPropertiesScan
@EnableScheduling
public class ArcaBackendApplication {

    @Value("${arca.evm.policy-proxy-address}")
    private String policyProxyAddress;

    @Value("${arca.evm.chain-id}")
    private Long chainId;

    public static void main(String[] args) {
        SpringApplication.run(ArcaBackendApplication.class, args);
    }

    @Bean
    public CommandLineRunner validateStartup() {
        return args -> {
            if (policyProxyAddress == null || policyProxyAddress.isBlank()) {
                throw new IllegalStateException("ARCA_POLICY_PROXY_ADDRESS must be configured");
            }
            if (chainId == null) {
                throw new IllegalStateException("ARCA_EVM_CHAIN_ID must be configured");
            }
            System.out.println("✓ Startup validation passed: chainId=" + chainId + ", proxy=" + policyProxyAddress);
        };
    }
}
