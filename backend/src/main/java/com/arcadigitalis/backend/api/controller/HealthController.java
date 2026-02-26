package com.arcadigitalis.backend.api.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.web3j.protocol.Web3j;

import javax.sql.DataSource;
import java.sql.Connection;
import java.util.Map;

@RestController
@RequestMapping("/health")
public class HealthController {

    @Autowired
    private DataSource dataSource;

    @Autowired
    private Web3j web3j;

    @GetMapping("/live")
    public ResponseEntity<Map<String, String>> liveness() {
        return ResponseEntity.ok(Map.of("status", "UP"));
    }

    @GetMapping("/ready")
    public ResponseEntity<Map<String, String>> readiness() {
        boolean dbReady = checkDatabase();
        boolean rpcReady = checkRpc();

        if (dbReady && rpcReady) {
            return ResponseEntity.ok(Map.of(
                "status", "UP",
                "database", "UP",
                "rpc", "UP"
            ));
        } else {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(Map.of(
                "status", "DOWN",
                "database", dbReady ? "UP" : "DOWN",
                "rpc", rpcReady ? "UP" : "DOWN"
            ));
        }
    }

    private boolean checkDatabase() {
        try (Connection connection = dataSource.getConnection()) {
            return connection.isValid(2);
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
