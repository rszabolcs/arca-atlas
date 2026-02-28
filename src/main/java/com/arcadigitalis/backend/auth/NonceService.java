package com.arcadigitalis.backend.auth;

import com.arcadigitalis.backend.api.exception.AuthException;
import com.arcadigitalis.backend.persistence.entity.NonceEntity;
import com.arcadigitalis.backend.persistence.repository.NonceRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;

/**
 * Issues and consumes single-use SIWE nonces. Nonces are stored in the DB
 * and consumed atomically during SIWE verification.
 */
@Service
public class NonceService {

    private static final Duration NONCE_TTL = Duration.ofMinutes(10);
    private static final int NONCE_BYTES = 32;

    private final NonceRepository nonceRepository;
    private final SecureRandom secureRandom = new SecureRandom();

    public NonceService(NonceRepository nonceRepository) {
        this.nonceRepository = nonceRepository;
    }

    /**
     * Issue a new nonce for the given wallet address.
     * Returns a NonceData record instead of the entity to avoid persistence leakage.
     */
    @Transactional
    public NonceData issueNonce(String walletAddress) {
        byte[] randomBytes = new byte[NONCE_BYTES];
        secureRandom.nextBytes(randomBytes);
        String nonceValue = Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);

        NonceEntity entity = new NonceEntity(
                walletAddress,
                nonceValue,
                Instant.now().plus(NONCE_TTL)
        );
        entity = nonceRepository.save(entity);
        return new NonceData(entity.getNonce(), entity.getExpiresAt());
    }

    public record NonceData(String nonce, Instant expiresAt) {}

    /**
     * Consume the nonce atomically. Throws AuthException if not found, expired, or already consumed.
     */
    @Transactional
    public void consumeNonce(String walletAddress, String nonce) {
        NonceEntity entity = nonceRepository
                .findByWalletAddressAndNonceAndConsumedFalse(walletAddress, nonce)
                .orElseThrow(() -> new AuthException("Nonce not found or already consumed"));

        if (Instant.now().isAfter(entity.getExpiresAt())) {
            throw new AuthException("Nonce has expired");
        }

        entity.markConsumed();
        nonceRepository.save(entity);
    }

    /**
     * Periodic cleanup of expired nonces (every 5 minutes).
     */
    @Scheduled(fixedRate = 300_000)
    @Transactional
    public void cleanupExpiredNonces() {
        nonceRepository.deleteExpiredBefore(Instant.now());
    }
}
