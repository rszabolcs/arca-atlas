package com.arcadigitalis.backend.api.controller;

import com.arcadigitalis.backend.api.dto.NonceRequest;
import com.arcadigitalis.backend.api.dto.NonceResponse;
import com.arcadigitalis.backend.api.dto.VerifyRequest;
import com.arcadigitalis.backend.api.dto.VerifyResponse;
import com.arcadigitalis.backend.auth.JwtService;
import com.arcadigitalis.backend.auth.NonceService;
import com.arcadigitalis.backend.auth.SiweVerifier;
import com.arcadigitalis.backend.persistence.entity.NonceEntity;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private final NonceService nonceService;
    private final SiweVerifier siweVerifier;
    private final JwtService jwtService;

    @Value("${arca.jwt.ttl-seconds}")
    private long jwtTtlSeconds;

    public AuthController(NonceService nonceService, SiweVerifier siweVerifier, JwtService jwtService) {
        this.nonceService = nonceService;
        this.siweVerifier = siweVerifier;
        this.jwtService = jwtService;
    }

    @PostMapping("/nonce")
    public NonceResponse issueNonce(@RequestBody NonceRequest request) {
        NonceEntity nonce = nonceService.issueNonce(request.walletAddress());
        return new NonceResponse(nonce.getNonce(), nonce.getExpiresAt());
    }

    @PostMapping("/verify")
    public VerifyResponse verify(@RequestBody VerifyRequest request) {
        // Verify SIWE signature and consume nonce
        String verifiedAddress = siweVerifier.verify(request.signedMessage(), request.signature());

        // Issue JWT
        String token = jwtService.issueToken(verifiedAddress);
        Instant expiresAt = Instant.now().plusSeconds(jwtTtlSeconds);

        return new VerifyResponse(token, expiresAt);
    }
}
