package com.arcadigitalis.backend.api.dto;

/**
 * Unsigned transaction payload returned by all tx/* endpoints.
 */
public record TxPayloadResponse(String to, String data, String value, String gasEstimate) {
    public TxPayloadResponse(String to, String data, String gasEstimate) {
        this(to, data, "0x0", gasEstimate);
    }
}
