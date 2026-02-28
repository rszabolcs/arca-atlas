package com.arcadigitalis.backend.api.controller;

import com.arcadigitalis.backend.api.dto.NonceRequest;
import com.arcadigitalis.backend.api.dto.NonceResponse;
import com.arcadigitalis.backend.api.dto.VerifyRequest;
import com.arcadigitalis.backend.api.dto.VerifyResponse;
import com.arcadigitalis.backend.api.exception.ValidationException;
import com.arcadigitalis.backend.auth.JwtService;
import com.arcadigitalis.backend.auth.NonceService;
import com.arcadigitalis.backend.auth.SiweVerifier;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/auth")
@Tag(name = "Authentication", description = "SIWE nonce issuance and JWT token exchange")
public class AuthController {

    private final NonceService nonceService;
    private final SiweVerifier siweVerifier;
    private final JwtService jwtService;

    public AuthController(NonceService nonceService, SiweVerifier siweVerifier, JwtService jwtService) {
        this.nonceService = nonceService;
        this.siweVerifier = siweVerifier;
        this.jwtService = jwtService;
    }

    @PostMapping("/nonce")
    @Operation(summary = "Issue a SIWE nonce", operationId = "issueNonce")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "Nonce issued"),
                   @ApiResponse(responseCode = "400", description = "Invalid wallet address")})
    public ResponseEntity<NonceResponse> issueNonce(@RequestBody NonceRequest request) {
        if (request.walletAddress() == null || !request.walletAddress().matches("^0x[0-9a-fA-F]{40}$")) {
            throw new ValidationException("Invalid wallet address format");
        }
        NonceService.NonceData nonce = nonceService.issueNonce(request.walletAddress());
        return ResponseEntity.ok(new NonceResponse(nonce.nonce(), nonce.expiresAt()));
    }

    @PostMapping("/verify")
    @Operation(summary = "Verify SIWE message and issue JWT", operationId = "verifySiwe")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "JWT token issued"),
                   @ApiResponse(responseCode = "401", description = "Signature verification failed")})
    public ResponseEntity<VerifyResponse> verify(@RequestBody VerifyRequest request) {
        if (request.message() == null || request.message().isBlank()) {
            throw new ValidationException("Message is required");
        }
        if (request.signature() == null || request.signature().isBlank()) {
            throw new ValidationException("Signature is required");
        }

        String walletAddress = siweVerifier.verify(request.message(), request.signature());
        JwtService.TokenResult tokenResult = jwtService.issueToken(walletAddress);

        return ResponseEntity.ok(new VerifyResponse(tokenResult.token(), tokenResult.expiresAt()));
    }
}
