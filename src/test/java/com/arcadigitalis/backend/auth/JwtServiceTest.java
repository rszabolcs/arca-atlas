package com.arcadigitalis.backend.auth;

import com.arcadigitalis.backend.api.exception.AuthException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for JwtService — T037.
 *
 * Tests: expired token rejected; valid token accepted; sub == wallet;
 * jti unique per issuance; replayed jti → AuthException on second call (SC-008).
 */
class JwtServiceTest {

    private JwtService jwtService;

    private static final String SECRET = "test-secret-key-that-is-at-least-256-bits-long-for-hmac-sha256";
    private static final long TTL = 3600; // 1 hour
    private static final String WALLET = "0x71C7656EC7ab88b098defB751B7401B5f6d8976F";

    @BeforeEach
    void setUp() {
        jwtService = new JwtService(SECRET, TTL);
    }

    @Test
    @DisplayName("Issue token contains correct sub claim")
    void issueToken_subClaimMatchesWallet() {
        JwtService.TokenResult result = jwtService.issueToken(WALLET);
        assertThat(result.token()).isNotBlank();
        assertThat(result.expiresAt()).isNotNull();

        // Verify the token is valid and returns without exception
        String verified = jwtService.verifyToken(result.token());
        assertThat(verified).isEqualTo(WALLET);
    }

    @Test
    @DisplayName("Valid token is accepted on first verification")
    void validToken_accepted() {
        JwtService.TokenResult result = jwtService.issueToken(WALLET);
        // First call should not throw
        assertThatCode(() -> jwtService.verifyToken(result.token()))
            .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Replayed jti is rejected on second verifyToken call (SC-008)")
    void replayedJti_rejectedOnSecondCall() {
        JwtService.TokenResult result = jwtService.issueToken(WALLET);

        // First call succeeds
        jwtService.verifyToken(result.token());

        // Second call with same token must reject (jti replay)
        assertThatThrownBy(() -> jwtService.verifyToken(result.token()))
            .isInstanceOf(AuthException.class)
            .hasMessageContaining("replay");
    }

    @Test
    @DisplayName("Each issued token has a unique jti")
    void issueToken_uniqueJti() {
        JwtService.TokenResult result1 = jwtService.issueToken(WALLET);
        JwtService.TokenResult result2 = jwtService.issueToken(WALLET);

        // Tokens should be different (different jti)
        assertThat(result1.token()).isNotEqualTo(result2.token());

        // Both should be independently verifiable
        jwtService.verifyToken(result1.token());
        jwtService.verifyToken(result2.token());
    }

    @Test
    @DisplayName("Expired token is rejected")
    void expiredToken_rejected() {
        // Create a service with 0 TTL to produce immediately-expired tokens
        JwtService shortLived = new JwtService(SECRET, 0);
        JwtService.TokenResult result = shortLived.issueToken(WALLET);

        // Token with 0 TTL will be expired immediately
        assertThatThrownBy(() -> shortLived.verifyToken(result.token()))
            .isInstanceOf(AuthException.class);
    }

    @Test
    @DisplayName("Malformed token is rejected")
    void malformedToken_rejected() {
        assertThatThrownBy(() -> jwtService.verifyToken("not.a.valid.jwt"))
            .isInstanceOf(AuthException.class);
    }

    @Test
    @DisplayName("Null token is rejected")
    void nullToken_rejected() {
        assertThatThrownBy(() -> jwtService.verifyToken(null))
            .isInstanceOf(AuthException.class);
    }
}
