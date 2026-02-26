package com.arcadigitalis.backend.auth;

import com.arcadigitalis.backend.api.exception.AuthException;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class JwtService {

    @Value("${arca.jwt.secret}")
    private String secret;

    @Value("${arca.jwt.ttl-seconds}")
    private long ttlSeconds;

    private final ConcurrentHashMap<String, Instant> usedJtis = new ConcurrentHashMap<>();

    public String issueToken(String walletAddress) {
        String jti = UUID.randomUUID().toString();
        Instant now = Instant.now();
        Instant expiry = now.plusSeconds(ttlSeconds);

        SecretKey key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));

        return Jwts.builder()
            .subject(walletAddress)
            .id(jti)
            .issuedAt(Date.from(now))
            .expiration(Date.from(expiry))
            .signWith(key, Jwts.SIG.HS256)
            .compact();
    }

    public String verifyToken(String token) {
        try {
            SecretKey key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));

            Claims claims = Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();

            String jti = claims.getId();
            String subject = claims.getSubject();
            Date expiration = claims.getExpiration();

            // Check expiry
            if (expiration.before(new Date())) {
                throw new AuthException("Token has expired");
            }

            // Check JTI replay (must be first use)
            if (usedJtis.containsKey(jti)) {
                throw new AuthException("Token has already been used (replay detected)");
            }

            // Record JTI with its expiry
            usedJtis.put(jti, expiration.toInstant());

            return subject;

        } catch (Exception e) {
            throw new AuthException("Invalid or expired token: " + e.getMessage());
        }
    }

    @Scheduled(fixedDelay = 60000) // Every 60 seconds
    public void pruneExpiredJtis() {
        Instant now = Instant.now();
        usedJtis.entrySet().removeIf(entry -> entry.getValue().isBefore(now));
    }
}
