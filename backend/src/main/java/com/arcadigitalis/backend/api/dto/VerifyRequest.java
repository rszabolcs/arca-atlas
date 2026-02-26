package com.arcadigitalis.backend.api.dto;

public record VerifyRequest(String walletAddress, String signedMessage, String signature) {
}
