package com.arcadigitalis.backend.api.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.web3j.protocol.Web3j;

import javax.sql.DataSource;
import java.sql.Connection;
import java.util.Map;

/**
 * Health endpoints for container orchestrator probes (NFR-004).
 */
@RestController
@RequestMapping("/health")
public class HealthController {

    private final DataSource dataSource;
    private final Web3j web3j;

    public HealthController(DataSource dataSource, Web3j web3j) {
        this.dataSource = dataSource;
        this.web3j = web3j;
    }

    /**
     * Liveness probe — always 200 if the process is running.
     */
    @GetMapping("/live")
    public ResponseEntity<Map<String, String>> liveness() {
        return ResponseEntity.ok(Map.of("status", "UP"));
    }

    /**
     * Readiness probe — 200 only when PostgreSQL + EVM RPC are reachable.
     */
    @GetMapping("/ready")
    public ResponseEntity<Map<String, Object>> readiness() {
        boolean dbReady = checkDatabase();
        boolean rpcReady = checkRpc();

        Map<String, Object> body = Map.of(
                "status", (dbReady && rpcReady) ? "UP" : "DOWN",
                "db", dbReady ? "UP" : "DOWN",
                "rpc", rpcReady ? "UP" : "DOWN"
        );

        if (dbReady && rpcReady) {
            return ResponseEntity.ok(body);
        }
        return ResponseEntity.status(503).body(body);
    }

    private boolean checkDatabase() {
        try (Connection conn = dataSource.getConnection()) {
            return conn.isValid(2);
        } catch (Exception e) {
            return false;
        }
    }

    private boolean checkRpc() {
        try {
            web3j.ethBlockNumber().send();
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
