package com.arcadigitalis.backend.notifications;

import com.arcadigitalis.backend.evm.IndexerPoller.IndexedEventNotification;
import com.arcadigitalis.backend.evm.Web3jConfig;
import com.arcadigitalis.backend.persistence.entity.NotificationTargetEntity;
import com.arcadigitalis.backend.persistence.repository.NotificationTargetRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Listens for indexed events from {@link com.arcadigitalis.backend.evm.IndexerPoller}
 * and dispatches notifications to active subscribers.
 * MUST NOT block the indexer thread.
 * MUST NOT propagate delivery exceptions to caller.
 */
@Component
public class NotificationDispatcher {

    private static final Logger log = LoggerFactory.getLogger(NotificationDispatcher.class);

    private final NotificationTargetRepository targetRepository;
    private final Web3jConfig config;
    private final EmailDelivery emailDelivery;
    private final WebhookDelivery webhookDelivery;
    private final PushDelivery pushDelivery;
    private final RetryPolicy retryPolicy;

    public NotificationDispatcher(NotificationTargetRepository targetRepository,
                                   Web3jConfig config,
                                   EmailDelivery emailDelivery,
                                   WebhookDelivery webhookDelivery,
                                   PushDelivery pushDelivery,
                                   RetryPolicy retryPolicy) {
        this.targetRepository = targetRepository;
        this.config = config;
        this.emailDelivery = emailDelivery;
        this.webhookDelivery = webhookDelivery;
        this.pushDelivery = pushDelivery;
        this.retryPolicy = retryPolicy;
    }

    @Async
    @EventListener
    public void onIndexedEvent(IndexedEventNotification notification) {
        try {
            var event = notification.event();
            String packageKey = event.packageKey();
            String eventType = event.eventType();

            // Look up active notification targets for this package
            List<NotificationTargetEntity> targets = targetRepository.findActiveByPackage(
                config.getChainId(), config.getProxyAddress(), packageKey);

            for (NotificationTargetEntity target : targets) {
                // Check if this target is subscribed to this event type
                String[] subscribedTypes = target.getEventTypes();
                if (subscribedTypes != null && !Arrays.asList(subscribedTypes).contains(eventType)) {
                    continue;
                }

                dispatch(target, packageKey, eventType, event.rawData());
            }
        } catch (Exception e) {
            log.warn("Error dispatching notifications: {}", e.getMessage());
            // MUST NOT propagate
        }
    }

    private void dispatch(NotificationTargetEntity target, String packageKey,
                          String eventType, Map<String, Object> eventData) {
        String channelType = target.getChannelType();
        String channelValue = target.getChannelValue();
        String description = channelType + ":" + channelValue + " for " + eventType;

        Map<String, Object> safeData = eventData != null ? eventData : Collections.emptyMap();

        retryPolicy.executeWithRetry(
            () -> {
                switch (channelType) {
                    case "email" -> emailDelivery.send(channelValue, packageKey, eventType, safeData);
                    case "webhook" -> webhookDelivery.post(channelValue, packageKey, eventType, safeData);
                    case "push" -> pushDelivery.send(channelValue, packageKey, eventType, safeData);
                    default -> log.warn("Unknown channel type: {}", channelType);
                }
            },
            () -> {
                // Record final failure in DB
                try {
                    target.setLastDeliveryAttempt(Instant.now());
                    target.setLastDeliveryStatus("failed");
                    targetRepository.save(target);
                } catch (Exception e) {
                    log.error("Failed to update delivery status for target {}: {}", target.getId(), e.getMessage());
                }
            },
            description
        );

        // Record successful delivery
        try {
            target.setLastDeliveryAttempt(Instant.now());
            target.setLastDeliveryStatus("delivered");
            targetRepository.save(target);
        } catch (Exception e) {
            log.debug("Failed to update delivery status: {}", e.getMessage());
        }
    }
}
