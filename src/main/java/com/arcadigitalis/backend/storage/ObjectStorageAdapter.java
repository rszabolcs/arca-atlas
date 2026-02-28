package com.arcadigitalis.backend.storage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.net.URI;

/**
 * Stores content to an S3-compatible backend. Returns an {@code s3://} URI.
 * Used as fallback when IPFS is not configured.
 */
@Component
public class ObjectStorageAdapter {

    private static final Logger log = LoggerFactory.getLogger(ObjectStorageAdapter.class);

    @Value("${arca.storage.s3.enabled:false}")
    private boolean enabled;

    @Value("${arca.storage.s3.bucket:arca-artifacts}")
    private String bucket;

    @Value("${arca.storage.s3.region:us-east-1}")
    private String region;

    @Value("${arca.storage.s3.endpoint:}")
    private String endpoint;

    public boolean isEnabled() {
        return enabled;
    }

    /**
     * Puts content to S3 with the sha256 hash as the key.
     * @return s3:// URI
     * @throws StorageException on failure
     */
    public String put(byte[] content, String sha256Key) {
        if (!enabled) throw new StorageException("S3 storage is not enabled");

        try {
            var builder = S3Client.builder().region(Region.of(region));
            if (endpoint != null && !endpoint.isBlank()) {
                builder.endpointOverride(URI.create(endpoint));
            }
            S3Client client = builder.build();

            PutObjectRequest request = PutObjectRequest.builder()
                .bucket(bucket)
                .key(sha256Key)
                .contentType("application/octet-stream")
                .build();

            client.putObject(request, RequestBody.fromBytes(content));

            String uri = "s3://" + bucket + "/" + sha256Key;
            log.info("Stored content in S3: {}", uri);
            return uri;
        } catch (Exception e) {
            throw new StorageException("S3 storage failed: " + e.getMessage(), e);
        }
    }
}
