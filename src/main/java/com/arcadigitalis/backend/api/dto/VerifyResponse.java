package com.arcadigitalis.backend.api.dto;

import java.time.Instant;

public record VerifyResponse(String token, Instant expiresAt) {}
