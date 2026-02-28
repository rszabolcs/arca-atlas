package com.arcadigitalis.backend.api.exception;

/**
 * Thrown when sha256 hash verification fails on artifact upload.
 * Maps to HTTP 422.
 */
public class IntegrityException extends RuntimeException {
    public IntegrityException(String message) {
        super(message);
    }
}
