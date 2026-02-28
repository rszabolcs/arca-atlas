package com.arcadigitalis.backend.auth;

import com.arcadigitalis.backend.api.exception.AuthException;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Issues and verifies HS256 JWT session tokens. Maintains an in-memory jti replay store
 * to prevent token replay within the TTL window (FR-004, SC-008).
 */
@Service
public class JwtService {

    private static final Logger log = LoggerFactory.getLogger(JwtService.class);

    private final SecretKey secretKey;
    private final long ttlSeconds;

    /** In-memory jti replay store: maps jti → expiry time. MVP implementation per FR-004. */
    private final ConcurrentHashMap<String, Instant> usedJtis = new ConcurrentHashMap<>();

    public JwtService(@Value("${arca.jwt.secret}") String secret,
                      @Value("${arca.jwt.ttl-seconds}") long ttlSeconds) {
        byte[] keyBytes = secret.getBytes(StandardCharsets.UTF_8);
        if (keyBytes.length < 32) {
            throw new IllegalStateException("ARCA_JWT_SECRET must be at least 32 bytes");
        }
        this.secretKey = Keys.hmacShaKeyFor(keyBytes);
        this.ttlSeconds = ttlSeconds;
    }

    /**
     * Issue a new JWT for the given wallet address.
     *
     * @return a record containing the token string and its expiry instant
     */
    public TokenResult issueToken(String walletAddress) {
        Instant now = Instant.now();
        Instant expiry = now.plusSeconds(ttlSeconds);
        String jti = UUID.randomUUID().toString();

        String token = Jwts.builder()
                .subject(walletAddress)
                .id(jti)
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiry))
                .signWith(secretKey)
                .compact();

        return new TokenResult(token, expiry);
    }

    /**
     * Verify a JWT and return the wallet address (subject claim).
     * Checks: signature, expiry, and jti replay.
     *
     * @throws AuthException if the token is invalid, expired, or replayed
     */
    public String verifyToken(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(secretKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();

            String jti = claims.getId();
            if (jti != null) {
                Instant expiry = claims.getExpiration().toInstant();
                // Check for replay: if jti is already in the store, reject
                Instant existing = usedJtis.putIfAbsent(jti, expiry);
                if (existing != null) {
                    throw new AuthException("Token has already been used (jti replay detected)");
                }
            }

            return claims.getSubject();
        } catch (AuthException e) {
            throw e;
        } catch (JwtException | IllegalArgumentException e) {
            throw new AuthException("Invalid or expired token: " + e.getMessage());
        }
    }

    /**
     * Periodic cleanup of expired jti entries (every 60 seconds).
     */
    @Scheduled(fixedRate = 60_000)
    public void pruneExpiredJtis() {
        Instant now = Instant.now();
        usedJtis.entrySet().removeIf(entry -> now.isAfter(entry.getValue()));
    }

    public record TokenResult(String token, Instant expiresAt) {}
}
