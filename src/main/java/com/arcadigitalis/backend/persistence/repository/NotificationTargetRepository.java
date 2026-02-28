package com.arcadigitalis.backend.persistence.repository;

import com.arcadigitalis.backend.persistence.entity.NotificationTargetEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface NotificationTargetRepository extends JpaRepository<NotificationTargetEntity, UUID> {

    @Query("SELECT n FROM NotificationTargetEntity n WHERE n.chainId = :chainId AND n.proxyAddress = :proxyAddress AND n.packageKey = :packageKey AND n.active = true")
    List<NotificationTargetEntity> findActiveByPackage(long chainId, String proxyAddress, String packageKey);

    List<NotificationTargetEntity> findBySubscriberAddress(String subscriberAddress);
}
