package com.arcadigitalis.backend.storage;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class ObjectStorageAdapter {

    @Value("${arca.s3.enabled:false}")
    private boolean enabled;

    @Value("${arca.s3.bucket:}")
    private String bucket;

    public String put(byte[] content, String sha256Key) {
        if (!enabled) {
            return null;
        }

        // TODO: Implement S3-compatible object storage
        // For now, return placeholder
        throw new IpfsAdapter.StorageException("S3 storage not yet implemented");
    }
}
