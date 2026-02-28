package com.arcadigitalis.backend.notifications;

/**
 * Thrown when a notification delivery fails.
 */
public class DeliveryException extends RuntimeException {
    public DeliveryException(String message) { super(message); }
    public DeliveryException(String message, Throwable cause) { super(message, cause); }
}
