package com.arcadigitalis.backend.api.exception;

/**
 * Thrown when authentication fails (invalid SIWE signature, expired nonce, domain mismatch, etc.).
 * Maps to HTTP 401.
 */
public class AuthException extends RuntimeException {
    public AuthException(String message) {
        super(message);
    }
    public AuthException(String message, Throwable cause) {
        super(message, cause);
    }
}
