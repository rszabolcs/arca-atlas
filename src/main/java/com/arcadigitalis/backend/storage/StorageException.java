package com.arcadigitalis.backend.storage;

/**
 * Thrown when a storage backend operation fails.
 */
public class StorageException extends RuntimeException {
    public StorageException(String message) { super(message); }
    public StorageException(String message, Throwable cause) { super(message, cause); }
}
