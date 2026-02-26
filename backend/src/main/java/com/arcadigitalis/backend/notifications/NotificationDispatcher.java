package com.arcadigitalis.backend.notifications;

import com.arcadigitalis.backend.evm.IndexerPoller;
import com.arcadigitalis.backend.persistence.entity.NotificationTargetEntity;
import com.arcadigitalis.backend.persistence.repository.NotificationTargetRepository;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class NotificationDispatcher {

    private final NotificationTargetRepository targetRepository;
    private final EmailDelivery emailDelivery;
    private final WebhookDelivery webhookDelivery;
    private final RetryPolicy retryPolicy;

    public NotificationDispatcher(NotificationTargetRepository targetRepository,
                                   EmailDelivery emailDelivery,
                                   WebhookDelivery webhookDelivery,
                                   RetryPolicy retryPolicy) {
        this.targetRepository = targetRepository;
        this.emailDelivery = emailDelivery;
        this.webhookDelivery = webhookDelivery;
        this.retryPolicy = retryPolicy;
    }

    @Async
    @EventListener
    public void onIndexedEvent(IndexerPoller.IndexedEventNotification notification) {
        var event = notification.event();

        // Find active notification targets for this package + event type
        List<NotificationTargetEntity> targets = targetRepository.findActiveByPackageKeyAndEventType(
            event.getChainId(),
            event.getProxyAddress(),
            event.getPackageKey(),
            event.getEventType()
        );

        // Dispatch to each target
        for (NotificationTargetEntity target : targets) {
            try {
                dispatchToTarget(target, event);
            } catch (Exception e) {
                // Log but don't propagate - notifications must not block indexer
                System.err.println("Notification dispatch failed for target " + target.getId() + ": " + e.getMessage());
            }
        }
    }

    private void dispatchToTarget(NotificationTargetEntity target, com.arcadigitalis.backend.persistence.entity.EventRecordEntity event) {
        retryPolicy.execute(() -> {
            switch (target.getChannelType()) {
                case "email" -> emailDelivery.send(target, event);
                case "webhook" -> webhookDelivery.post(target, event);
                case "push" -> {
                    // Push notifications not yet implemented
                    throw new DeliveryException("Push notifications not implemented");
                }
                default -> throw new DeliveryException("Unknown channel type: " + target.getChannelType());
            }
        }, target);
    }

    public static class DeliveryException extends RuntimeException {
        public DeliveryException(String message) {
            super(message);
        }

        public DeliveryException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
