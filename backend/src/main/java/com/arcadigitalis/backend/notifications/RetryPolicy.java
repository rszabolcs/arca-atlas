package com.arcadigitalis.backend.notifications;

import com.arcadigitalis.backend.persistence.entity.NotificationTargetEntity;
import com.arcadigitalis.backend.persistence.repository.NotificationTargetRepository;
import org.springframework.stereotype.Service;

@Service
public class RetryPolicy {

    private final NotificationTargetRepository targetRepository;
    private static final int MAX_ATTEMPTS = 3;
    private static final long INITIAL_BACKOFF_MS = 1000;

    public RetryPolicy(NotificationTargetRepository targetRepository) {
        this.targetRepository = targetRepository;
    }

    public void execute(Runnable delivery, NotificationTargetEntity target) {
        int attempt = 0;
        Exception lastException = null;

        while (attempt < MAX_ATTEMPTS) {
            try {
                delivery.run();

                // Success - update status
                target.setLastDeliveryStatus("success");
                targetRepository.save(target);
                return;

            } catch (Exception e) {
                lastException = e;
                attempt++;

                if (attempt < MAX_ATTEMPTS) {
                    // Exponential backoff
                    long backoffMs = INITIAL_BACKOFF_MS * (long) Math.pow(2, attempt - 1);
                    try {
                        Thread.sleep(backoffMs);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        }

        // All attempts failed
        target.setLastDeliveryStatus("failed");
        targetRepository.save(target);

        System.err.println("Notification delivery failed after " + MAX_ATTEMPTS +
            " attempts for target " + target.getId() + ": " + lastException.getMessage());
    }
}
