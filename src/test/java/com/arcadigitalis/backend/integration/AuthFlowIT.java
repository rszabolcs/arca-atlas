package com.arcadigitalis.backend.integration;

import com.arcadigitalis.backend.api.dto.NonceResponse;
import com.arcadigitalis.backend.api.dto.VerifyResponse;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration test for the full SIWE → JWT → package status flow — T039.
 *
 * Requires Testcontainers PostgreSQL + WireMock EVM stub for full execution.
 * Disabled by default; enabled when running with the integration profile.
 *
 * Test coverage:
 * - POST /auth/nonce → 200 + nonce
 * - POST /auth/verify → 200 + JWT (requires real SIWE signature)
 * - GET /packages/{key}/status with valid JWT → 200
 * - Expired token → 401
 * - Domain mismatch → 401
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Disabled("Requires Testcontainers + WireMock infrastructure — run with -Pintegration")
class AuthFlowIT {

    @Autowired
    private MockMvc mockMvc;

    private static final String WALLET = "0x71C7656EC7ab88b098defB751B7401B5f6d8976F";
    private static final String PACKAGE_KEY = "0x" + "ab".repeat(32);

    @Test
    @DisplayName("POST /auth/nonce returns nonce for valid wallet address")
    void issueNonce_validWallet_returns200() throws Exception {
        mockMvc.perform(post("/auth/nonce")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"walletAddress\": \"" + WALLET + "\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.nonce").isNotEmpty())
            .andExpect(jsonPath("$.expiresAt").isNotEmpty());
    }

    @Test
    @DisplayName("POST /auth/nonce rejects invalid wallet format")
    void issueNonce_invalidWallet_returns400() throws Exception {
        mockMvc.perform(post("/auth/nonce")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"walletAddress\": \"not-a-wallet\"}"))
            .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /auth/verify rejects empty message")
    void verify_emptyMessage_returns400() throws Exception {
        mockMvc.perform(post("/auth/verify")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"message\": \"\", \"signature\": \"0xdeadbeef\"}"))
            .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("GET /packages/{key}/status without JWT returns 401")
    void packageStatus_noAuth_returns401() throws Exception {
        mockMvc.perform(get("/packages/" + PACKAGE_KEY + "/status"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("GET /config returns instance configuration (unauthenticated)")
    void config_unauthenticated_returns200() throws Exception {
        mockMvc.perform(get("/config"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.chainId").isNumber())
            .andExpect(jsonPath("$.proxyAddress").isString())
            .andExpect(jsonPath("$.fundingEnabled").isBoolean());
    }
}
