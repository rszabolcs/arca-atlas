package com.arcadigitalis.backend.storage;

import com.arcadigitalis.backend.api.dto.ArtifactUploadResponse;
import com.arcadigitalis.backend.api.exception.IntegrityException;
import com.arcadigitalis.backend.persistence.entity.StoredArtifactEntity;
import com.arcadigitalis.backend.persistence.repository.StoredArtifactRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;

@Service
public class ArtifactService {

    private final StoredArtifactRepository repository;
    private final IpfsAdapter ipfsAdapter;
    private final ObjectStorageAdapter objectStorageAdapter;

    @Value("${arca.evm.chain-id}")
    private Long chainId;

    @Value("${arca.evm.policy-proxy-address}")
    private String proxyAddress;

    public ArtifactService(StoredArtifactRepository repository, IpfsAdapter ipfsAdapter,
                            ObjectStorageAdapter objectStorageAdapter) {
        this.repository = repository;
        this.ipfsAdapter = ipfsAdapter;
        this.objectStorageAdapter = objectStorageAdapter;
    }

    public ArtifactUploadResponse pin(String packageKey, String artifactType, String declaredHash, byte[] content) {
        // Compute actual hash
        String actualHash = computeSha256(content);

        // Verify integrity
        if (!actualHash.equals(declaredHash)) {
            throw new IntegrityException("Hash mismatch: declared=" + declaredHash + ", actual=" + actualHash);
        }

        // Pin to IPFS and/or S3
        String ipfsUri = null;
        String s3Uri = null;

        try {
            ipfsUri = ipfsAdapter.pin(content);
        } catch (Exception e) {
            // IPFS failed, try S3
        }

        if (ipfsUri == null) {
            try {
                s3Uri = objectStorageAdapter.put(content, actualHash);
            } catch (Exception e) {
                throw new IpfsAdapter.StorageException("Both IPFS and S3 storage failed");
            }
        }

        // Persist metadata
        StoredArtifactEntity entity = new StoredArtifactEntity();
        entity.setChainId(chainId);
        entity.setProxyAddress(proxyAddress);
        entity.setPackageKey(packageKey);
        entity.setArtifactType(artifactType);
        entity.setSha256Hash(actualHash);
        entity.setSizeBytes((long) content.length);
        entity.setIpfsUri(ipfsUri);
        entity.setS3Uri(s3Uri);
        entity.setStorageConfirmedAt(Instant.now());

        repository.save(entity);

        return new ArtifactUploadResponse(
            entity.getId(),
            ipfsUri != null ? ipfsUri : s3Uri,
            actualHash,
            (long) content.length
        );
    }

    public byte[] retrieve(UUID id) {
        Optional<StoredArtifactEntity> entity = repository.findById(id);
        if (entity.isEmpty()) {
            throw new IllegalArgumentException("Artifact not found: " + id);
        }

        // TODO: Implement actual retrieval from IPFS/S3
        throw new UnsupportedOperationException("Artifact retrieval not yet implemented");
    }

    private String computeSha256(byte[] content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(content);
            return HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            throw new RuntimeException("Failed to compute SHA-256", e);
        }
    }
}
