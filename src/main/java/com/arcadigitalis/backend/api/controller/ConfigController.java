package com.arcadigitalis.backend.api.controller;

import com.arcadigitalis.backend.api.dto.ConfigResponse;
import com.arcadigitalis.backend.evm.Web3jConfig;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Unauthenticated config endpoint (FR-010a).
 */
@RestController
@Tag(name = "Configuration", description = "Instance configuration and funding status")
public class ConfigController {

    private final Web3jConfig config;

    public ConfigController(Web3jConfig config) {
        this.config = config;
    }

    @GetMapping("/config")
    @Operation(summary = "Instance configuration and funding status", operationId = "getConfig")
    public ResponseEntity<ConfigResponse> getConfig() {
        return ResponseEntity.ok(new ConfigResponse(
            config.getChainId(),
            config.getProxyAddress(),
            config.isFundingEnabled()
        ));
    }
}
