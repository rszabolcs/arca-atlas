package com.arcadigitalis.backend.persistence.repository;

import com.arcadigitalis.backend.persistence.entity.NonceEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface NonceRepository extends JpaRepository<NonceEntity, UUID> {

    Optional<NonceEntity> findByNonceAndConsumedFalse(String nonce);

    @Modifying
    @Query("UPDATE NonceEntity n SET n.consumed = true WHERE n.id = :id")
    void markConsumed(UUID id);

    @Modifying
    @Query("DELETE FROM NonceEntity n WHERE n.expiresAt < :now")
    void deleteExpired(Instant now);
}
