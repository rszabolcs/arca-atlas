package com.arcadigitalis.backend.auth;

import com.arcadigitalis.backend.api.exception.AuthException;
import com.arcadigitalis.backend.persistence.entity.NonceEntity;
import com.arcadigitalis.backend.persistence.repository.NonceRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;

@Service
public class NonceService {

    private final NonceRepository nonceRepository;
    private final SecureRandom secureRandom = new SecureRandom();
    private static final int NONCE_TTL_MINUTES = 10;

    public NonceService(NonceRepository nonceRepository) {
        this.nonceRepository = nonceRepository;
    }

    @Transactional
    public NonceEntity issueNonce(String walletAddress) {
        String nonce = generateNonce();
        Instant expiresAt = Instant.now().plus(NONCE_TTL_MINUTES, ChronoUnit.MINUTES);

        NonceEntity entity = new NonceEntity(walletAddress, nonce, expiresAt);
        return nonceRepository.save(entity);
    }

    @Transactional
    public void consumeNonce(String walletAddress, String nonce) {
        NonceEntity entity = nonceRepository.findByNonceAndConsumedFalse(nonce)
            .orElseThrow(() -> new AuthException("Invalid or already consumed nonce"));

        if (!entity.getWalletAddress().equalsIgnoreCase(walletAddress)) {
            throw new AuthException("Nonce does not belong to this wallet");
        }

        if (Instant.now().isAfter(entity.getExpiresAt())) {
            throw new AuthException("Nonce has expired");
        }

        nonceRepository.markConsumed(entity.getId());
    }

    private String generateNonce() {
        byte[] randomBytes = new byte[32];
        secureRandom.nextBytes(randomBytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
    }

    @Transactional
    public void cleanupExpiredNonces() {
        nonceRepository.deleteExpired(Instant.now());
    }
}
