package com.arcadigitalis.backend.persistence.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "nonces", indexes = {
    @Index(name = "idx_nonces_nonce", columnList = "nonce", unique = true)
})
public class NonceEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "wallet_address", nullable = false, length = 42)
    private String walletAddress;

    @Column(name = "nonce", nullable = false, unique = true, length = 64)
    private String nonce;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "consumed", nullable = false)
    private boolean consumed = false;

    protected NonceEntity() {}

    public NonceEntity(String walletAddress, String nonce, Instant expiresAt) {
        this.walletAddress = walletAddress;
        this.nonce = nonce;
        this.createdAt = Instant.now();
        this.expiresAt = expiresAt;
        this.consumed = false;
    }

    public UUID getId() { return id; }
    public String getWalletAddress() { return walletAddress; }
    public String getNonce() { return nonce; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getExpiresAt() { return expiresAt; }
    public boolean isConsumed() { return consumed; }

    public void markConsumed() { this.consumed = true; }
}
