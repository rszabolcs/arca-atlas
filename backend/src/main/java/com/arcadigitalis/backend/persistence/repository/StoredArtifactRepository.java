package com.arcadigitalis.backend.persistence.repository;

import com.arcadigitalis.backend.persistence.entity.StoredArtifactEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface StoredArtifactRepository extends JpaRepository<StoredArtifactEntity, UUID> {

    Optional<StoredArtifactEntity> findBySha256Hash(String sha256Hash);

    boolean existsBySha256Hash(String sha256Hash);
}
