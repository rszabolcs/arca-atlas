package com.arcadigitalis.backend.api.dto;

import java.time.Instant;

public record NonceResponse(String nonce, Instant expiresAt) {}
