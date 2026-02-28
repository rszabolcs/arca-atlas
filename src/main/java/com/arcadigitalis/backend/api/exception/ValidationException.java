package com.arcadigitalis.backend.api.exception;

/**
 * Thrown when input validation fails (bad format, wrong chainId/proxyAddress, too many guardians, etc.).
 * Maps to HTTP 400.
 */
public class ValidationException extends RuntimeException {
    public ValidationException(String message) {
        super(message);
    }
}
