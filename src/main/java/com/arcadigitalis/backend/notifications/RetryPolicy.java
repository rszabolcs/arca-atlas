package com.arcadigitalis.backend.notifications;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Bounded exponential-backoff retry for notification delivery.
 * After final failure: logs WARN, never rethrows.
 * Dead-letter record preserved via callback.
 */
@Component
public class RetryPolicy {

    private static final Logger log = LoggerFactory.getLogger(RetryPolicy.class);

    @Value("${arca.notifications.retry.max-attempts:3}")
    private int maxAttempts;

    @Value("${arca.notifications.retry.base-delay-ms:1000}")
    private long baseDelayMs;

    /**
     * Executes the action with bounded exponential-backoff retry.
     * On final failure, invokes onFinalFailure and logs WARN.
     * Never throws — catches all DeliveryException instances.
     *
     * @param action the delivery action to retry
     * @param onFinalFailure callback invoked on final failure (e.g., to update DB)
     * @param description human-readable description for logging
     */
    public void executeWithRetry(Runnable action, Runnable onFinalFailure, String description) {
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                action.run();
                return; // Success
            } catch (DeliveryException e) {
                log.debug("Delivery attempt {}/{} failed for {}: {}", attempt, maxAttempts, description, e.getMessage());
                if (attempt == maxAttempts) {
                    log.warn("All {} delivery attempts exhausted for {}. Last error: {}",
                        maxAttempts, description, e.getMessage());
                    try {
                        onFinalFailure.run();
                    } catch (Exception fe) {
                        log.error("Failed to record delivery failure for {}: {}", description, fe.getMessage());
                    }
                    return;
                }
                // Exponential backoff
                try {
                    long delay = baseDelayMs * (1L << (attempt - 1));
                    Thread.sleep(delay);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    return;
                }
            } catch (Exception e) {
                log.warn("Unexpected error during delivery for {}: {}", description, e.getMessage());
                return;
            }
        }
    }
}
