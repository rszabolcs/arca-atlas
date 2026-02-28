package com.arcadigitalis.backend.api.exception;

/**
 * Thrown when the EVM RPC node is unreachable and a live read is required.
 * Maps to HTTP 503.
 */
public class RpcUnavailableException extends RuntimeException {
    public RpcUnavailableException(String message) {
        super(message);
    }
    public RpcUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
