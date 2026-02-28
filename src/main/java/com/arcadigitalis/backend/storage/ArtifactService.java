package com.arcadigitalis.backend.storage;

import com.arcadigitalis.backend.api.exception.IntegrityException;
import com.arcadigitalis.backend.persistence.entity.StoredArtifactEntity;
import com.arcadigitalis.backend.persistence.repository.StoredArtifactRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Artifact storage service — pins manifest/ciphertext blobs with sha256 verification.
 * MUST NOT attempt decryption or content inspection (FR-021, Constitution I).
 */
@Service
public class ArtifactService {

    private static final Logger log = LoggerFactory.getLogger(ArtifactService.class);

    private final IpfsAdapter ipfsAdapter;
    private final ObjectStorageAdapter objectStorageAdapter;
    private final StoredArtifactRepository artifactRepository;

    public ArtifactService(IpfsAdapter ipfsAdapter, ObjectStorageAdapter objectStorageAdapter,
                           StoredArtifactRepository artifactRepository) {
        this.ipfsAdapter = ipfsAdapter;
        this.objectStorageAdapter = objectStorageAdapter;
        this.artifactRepository = artifactRepository;
    }

    /**
     * Pins artifact content and persists metadata.
     * Verifies sha256 before confirming storage.
     *
     * @throws IntegrityException if declared hash does not match computed hash
     */
    public ArtifactData pin(String artifactType, String declaredHash, byte[] content,
                                     Long chainId, String proxyAddress, String packageKey) {
        // Compute sha256
        String computedHash = computeSha256(content);

        // Compare against declared hash
        if (!normalizeHash(computedHash).equalsIgnoreCase(normalizeHash(declaredHash))) {
            throw new IntegrityException(
                "SHA-256 mismatch: declared=" + declaredHash + " computed=0x" + computedHash
            );
        }

        // Check for existing artifact with same hash (content-addressed dedup)
        String hashWithPrefix = declaredHash.startsWith("0x") ? declaredHash : "0x" + declaredHash;
        Optional<StoredArtifactEntity> existing = artifactRepository.findBySha256Hash(hashWithPrefix);
        if (existing.isPresent()) {
            log.info("Artifact already exists with hash={}, returning existing", hashWithPrefix);
            return toData(existing.get());
        }

        // Pin to backends
        String ipfsUri = null;
        String s3Uri = null;

        if (ipfsAdapter.isEnabled()) {
            try {
                ipfsUri = ipfsAdapter.pin(content);
            } catch (StorageException e) {
                log.warn("IPFS pin failed: {}", e.getMessage());
            }
        }

        if (objectStorageAdapter.isEnabled()) {
            try {
                s3Uri = objectStorageAdapter.put(content, hashWithPrefix);
            } catch (StorageException e) {
                log.warn("S3 storage failed: {}", e.getMessage());
            }
        }

        // Persist metadata
        StoredArtifactEntity entity = new StoredArtifactEntity(artifactType, hashWithPrefix, (long) content.length);

        if (chainId != null) entity.setChainId(chainId);
        if (proxyAddress != null) entity.setProxyAddress(proxyAddress);
        if (packageKey != null) entity.setPackageKey(packageKey);
        if (ipfsUri != null) entity.setIpfsUri(ipfsUri);
        if (s3Uri != null) entity.setS3Uri(s3Uri);

        if (ipfsUri != null || s3Uri != null) {
            entity.setStorageConfirmedAt(Instant.now());
        }

        return toData(artifactRepository.save(entity));
    }

    /**
     * Retrieves artifact metadata by ID.
     */
    public Optional<ArtifactData> retrieve(UUID id) {
        return artifactRepository.findById(id).map(this::toData);
    }

    private ArtifactData toData(StoredArtifactEntity entity) {
        return new ArtifactData(
            entity.getId().toString(), entity.getArtifactType(), entity.getSha256Hash(),
            entity.getIpfsUri(), entity.getS3Uri(), entity.getSizeBytes(),
            entity.getCreatedAt(), entity.getStorageConfirmedAt()
        );
    }

    public record ArtifactData(String id, String artifactType, String sha256Hash,
                                String ipfsUri, String s3Uri, Long sizeBytes,
                                Instant createdAt, Instant storageConfirmedAt) {}

    private static String computeSha256(byte[] content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(content);
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }

    private static String normalizeHash(String hash) {
        if (hash == null) return "";
        return hash.startsWith("0x") ? hash.substring(2) : hash;
    }
}
