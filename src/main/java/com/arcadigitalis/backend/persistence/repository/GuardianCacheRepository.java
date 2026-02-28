package com.arcadigitalis.backend.persistence.repository;

import com.arcadigitalis.backend.persistence.entity.GuardianCacheEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface GuardianCacheRepository extends JpaRepository<GuardianCacheEntity, UUID> {

    List<GuardianCacheEntity> findByPackageCacheIdOrderByPosition(UUID packageCacheId);

    void deleteByPackageCacheId(UUID packageCacheId);
}
