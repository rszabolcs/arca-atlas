package com.arcadigitalis.backend.storage;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class IpfsAdapter {

    @Value("${arca.ipfs.enabled:false}")
    private boolean enabled;

    @Value("${arca.ipfs.api-url:}")
    private String apiUrl;

    public String pin(byte[] content) {
        if (!enabled) {
            return null;
        }

        // TODO: Implement IPFS pinning via HTTP multipart to Infura/Pinata
        // For now, return placeholder
        throw new StorageException("IPFS pinning not yet implemented");
    }

    public static class StorageException extends RuntimeException {
        public StorageException(String message) {
            super(message);
        }
    }
}
