package com.arcadigitalis.backend.persistence.repository;

import com.arcadigitalis.backend.persistence.entity.NotificationTargetEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface NotificationTargetRepository extends JpaRepository<NotificationTargetEntity, UUID> {

    @Query("SELECT nt FROM NotificationTargetEntity nt WHERE nt.chainId = :chainId AND nt.proxyAddress = :proxyAddress AND nt.packageKey = :packageKey AND nt.active = true AND :eventType = ANY(nt.eventTypes)")
    List<NotificationTargetEntity> findActiveByPackageKeyAndEventType(
        Long chainId,
        String proxyAddress,
        String packageKey,
        String eventType
    );

    List<NotificationTargetEntity> findBySubscriberAddress(String subscriberAddress);
}
