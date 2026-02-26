package com.arcadigitalis.backend.api.exception;

public class RpcUnavailableException extends RuntimeException {
    public RpcUnavailableException(String message) {
        super(message);
    }

    public RpcUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
