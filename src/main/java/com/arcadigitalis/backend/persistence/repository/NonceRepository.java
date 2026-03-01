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

    Optional<NonceEntity> findByWalletAddressAndNonceAndConsumedFalse(String walletAddress, String nonce);

    @Modifying
    @Query("DELETE FROM NonceEntity n WHERE n.expiresAt < :cutoff")
    int deleteExpiredBefore(Instant cutoff);
}
