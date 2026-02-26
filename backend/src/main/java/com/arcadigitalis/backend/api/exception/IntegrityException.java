package com.arcadigitalis.backend.api.exception;

public class IntegrityException extends RuntimeException {
    public IntegrityException(String message) {
        super(message);
    }
}
