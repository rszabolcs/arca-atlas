package com.arcadigitalis.backend.notifications;

import com.arcadigitalis.backend.evm.PolicyReader;
import com.arcadigitalis.backend.evm.Web3jConfig;
import com.arcadigitalis.backend.persistence.entity.NotificationTargetEntity;
import com.arcadigitalis.backend.persistence.repository.NotificationTargetRepository;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Service layer for notification subscription CRUD.
 * Wraps persistence access so api package stays free of entity/repository imports.
 */
@Service
public class NotificationSubscriptionService {

    private static final Set<String> VALID_CHANNEL_TYPES = Set.of("email", "webhook", "push");
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
    private static final Pattern URL_PATTERN = Pattern.compile("^https?://\\S+$");

    private final NotificationTargetRepository targetRepository;
    private final PolicyReader policyReader;
    private final Web3jConfig config;

    public NotificationSubscriptionService(NotificationTargetRepository targetRepository,
                                           PolicyReader policyReader,
                                           Web3jConfig config) {
        this.targetRepository = targetRepository;
        this.policyReader = policyReader;
        this.config = config;
    }

    public record Subscription(
        String id, long chainId, String proxyAddress, String packageKey,
        String subscriberAddress, List<String> eventTypes, String channelType,
        String channelValue, boolean active, Instant createdAt,
        Instant lastDeliveryAttempt, String lastDeliveryStatus
    ) {}

    /**
     * Creates a new subscription after verifying the caller is the on-chain owner or beneficiary.
     */
    public Subscription create(String packageKey, String callerAddress,
                               List<String> eventTypes, String channelType, String channelValue) {
        validateChannel(channelType, channelValue);

        PolicyReader.PackageView view = policyReader.getPackage(packageKey);
        boolean isOwner = callerAddress.equalsIgnoreCase(view.ownerAddress());
        boolean isBeneficiary = callerAddress.equalsIgnoreCase(view.beneficiaryAddress());
        if (!isOwner && !isBeneficiary) {
            throw new AccessDeniedException(
                "Caller is not the owner or beneficiary of package " + packageKey);
        }

        NotificationTargetEntity entity = new NotificationTargetEntity(
            config.getChainId(), config.getProxyAddress(), packageKey,
            callerAddress, eventTypes.toArray(new String[0]),
            channelType, channelValue
        );
        entity = targetRepository.save(entity);
        return toSubscription(entity);
    }

    /**
     * Updates an existing subscription (only the subscriber may update).
     */
    public Optional<Subscription> update(UUID id, String callerAddress,
                                         List<String> eventTypes, String channelValue, Boolean active) {
        NotificationTargetEntity entity = targetRepository.findById(id).orElse(null);
        if (entity == null) return Optional.empty();

        if (!entity.getSubscriberAddress().equalsIgnoreCase(callerAddress)) {
            throw new AccessDeniedException("Not the subscription owner");
        }

        if (eventTypes != null) entity.setEventTypes(eventTypes.toArray(new String[0]));
        if (channelValue != null) entity.setChannelValue(channelValue);
        if (active != null) entity.setActive(active);

        entity = targetRepository.save(entity);
        return Optional.of(toSubscription(entity));
    }

    /**
     * Deletes a subscription (only the subscriber may delete). Returns true if found & deleted.
     */
    public boolean delete(UUID id, String callerAddress) {
        NotificationTargetEntity entity = targetRepository.findById(id).orElse(null);
        if (entity == null) return false;

        if (!entity.getSubscriberAddress().equalsIgnoreCase(callerAddress)) {
            throw new AccessDeniedException("Not the subscription owner");
        }

        targetRepository.delete(entity);
        return true;
    }

    private Subscription toSubscription(NotificationTargetEntity entity) {
        return new Subscription(
            entity.getId().toString(),
            entity.getChainId(),
            entity.getProxyAddress(),
            entity.getPackageKey(),
            entity.getSubscriberAddress(),
            Arrays.asList(entity.getEventTypes()),
            entity.getChannelType(),
            entity.getChannelValue(),
            entity.isActive(),
            entity.getCreatedAt(),
            entity.getLastDeliveryAttempt(),
            entity.getLastDeliveryStatus()
        );
    }

    /**
     * Validates channel_type ∈ {email, webhook, push} and applies format guards on channel_value.
     */
    static void validateChannel(String channelType, String channelValue) {
        if (channelType == null || !VALID_CHANNEL_TYPES.contains(channelType)) {
            throw new IllegalArgumentException("channelType must be 'email', 'webhook', or 'push'");
        }
        if (channelValue == null || channelValue.isBlank()) {
            throw new IllegalArgumentException("channelValue must not be empty");
        }
        switch (channelType) {
            case "email" -> {
                if (!EMAIL_PATTERN.matcher(channelValue).matches()) {
                    throw new IllegalArgumentException("channelValue must be a valid email address for channel type 'email'");
                }
            }
            case "webhook" -> {
                if (!URL_PATTERN.matcher(channelValue).matches()) {
                    throw new IllegalArgumentException("channelValue must be a valid HTTP(S) URL for channel type 'webhook'");
                }
            }
            case "push" -> {
                // Push: device token, just ensure non-empty (already checked above)
            }
        }
    }
}
