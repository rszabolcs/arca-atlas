package com.arcadigitalis.backend.api.controller;

import com.arcadigitalis.backend.api.dto.NotificationTargetRequest;
import com.arcadigitalis.backend.api.dto.NotificationTargetResponse;
import com.arcadigitalis.backend.evm.PolicyReader;
import com.arcadigitalis.backend.persistence.entity.NotificationTargetEntity;
import com.arcadigitalis.backend.persistence.repository.NotificationTargetRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/notification-targets")
public class NotificationController {

    private final NotificationTargetRepository targetRepository;
    private final PolicyReader policyReader;

    @Value("${arca.evm.chain-id}")
    private Long chainId;

    @Value("${arca.evm.policy-proxy-address}")
    private String proxyAddress;

    public NotificationController(NotificationTargetRepository targetRepository, PolicyReader policyReader) {
        this.targetRepository = targetRepository;
        this.policyReader = policyReader;
    }

    @PostMapping
    public NotificationTargetResponse create(
        @RequestBody NotificationTargetRequest request,
        @RequestAttribute("sessionAddress") String sessionAddress
    ) {
        // Verify caller is owner or beneficiary
        PolicyReader.PackageView packageView = policyReader.getPackage(request.packageKey());

        boolean isOwner = sessionAddress.equalsIgnoreCase(packageView.ownerAddress());
        boolean isBeneficiary = sessionAddress.equalsIgnoreCase(packageView.beneficiaryAddress());

        if (!isOwner && !isBeneficiary) {
            throw new AccessDeniedException("Only owner or beneficiary can create notification targets");
        }

        NotificationTargetEntity entity = new NotificationTargetEntity();
        entity.setChainId(chainId);
        entity.setProxyAddress(proxyAddress);
        entity.setPackageKey(request.packageKey());
        entity.setSubscriberAddress(sessionAddress);
        entity.setEventTypes(request.eventTypes());
        entity.setChannelType(request.channelType());
        entity.setChannelValue(request.channelValue());
        entity.setActive(true);

        targetRepository.save(entity);

        return new NotificationTargetResponse(
            entity.getId(),
            entity.getPackageKey(),
            entity.getEventTypes(),
            entity.getChannelType(),
            entity.isActive(),
            entity.getCreatedAt()
        );
    }

    @GetMapping
    public List<NotificationTargetResponse> list(@RequestAttribute("sessionAddress") String sessionAddress) {
        return targetRepository.findBySubscriberAddress(sessionAddress).stream()
            .map(entity -> new NotificationTargetResponse(
                entity.getId(),
                entity.getPackageKey(),
                entity.getEventTypes(),
                entity.getChannelType(),
                entity.isActive(),
                entity.getCreatedAt()
            ))
            .toList();
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable UUID id, @RequestAttribute("sessionAddress") String sessionAddress) {
        NotificationTargetEntity entity = targetRepository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Notification target not found"));

        if (!entity.getSubscriberAddress().equalsIgnoreCase(sessionAddress)) {
            throw new AccessDeniedException("Can only delete own notification targets");
        }

        targetRepository.delete(entity);
    }
}
