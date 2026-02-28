package com.arcadigitalis.backend.api.exception;

/**
 * Thrown when a live status read detects a state that would cause a predictable on-chain revert.
 * Maps to HTTP 409.
 */
public class ConflictException extends RuntimeException {
    private final String currentStatus;

    public ConflictException(String message, String currentStatus) {
        super(message);
        this.currentStatus = currentStatus;
    }

    public String getCurrentStatus() { return currentStatus; }
}
