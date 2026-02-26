package com.arcadigitalis.backend.api.dto;

public record TxPayloadResponse(
    String to,
    String data,
    String value,
    String gasEstimate
) {
}
