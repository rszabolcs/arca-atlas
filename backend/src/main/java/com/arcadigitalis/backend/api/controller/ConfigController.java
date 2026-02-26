package com.arcadigitalis.backend.api.controller;

import com.arcadigitalis.backend.api.dto.ConfigResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/config")
public class ConfigController {

    @Value("${arca.evm.chain-id}")
    private Long chainId;

    @Value("${arca.evm.policy-proxy-address}")
    private String proxyAddress;

    private final boolean fundingEnabled;

    public ConfigController(boolean fundingEnabled) {
        this.fundingEnabled = fundingEnabled;
    }

    @GetMapping
    public ConfigResponse getConfig() {
        return new ConfigResponse(chainId, proxyAddress, fundingEnabled);
    }
}
