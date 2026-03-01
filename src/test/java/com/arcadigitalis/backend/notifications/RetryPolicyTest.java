package com.arcadigitalis.backend.notifications;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for RetryPolicy — T089.
 * Tests: immediate success, retry on failure, final failure callback.
 */
class RetryPolicyTest {

    private RetryPolicy retryPolicy;

    @BeforeEach
    void setUp() {
        retryPolicy = new RetryPolicy();
        // Use reflection or test configuration to set maxAttempts=3, baseDelayMs=1
        // For unit tests, we'll just use default constructor — values from @Value annotations
        // won't be injected, so we set them via reflection
        try {
            var maxField = RetryPolicy.class.getDeclaredField("maxAttempts");
            maxField.setAccessible(true);
            maxField.setInt(retryPolicy, 3);

            var delayField = RetryPolicy.class.getDeclaredField("baseDelayMs");
            delayField.setAccessible(true);
            delayField.setLong(retryPolicy, 1L); // 1ms for fast tests
        } catch (Exception e) {
            throw new RuntimeException("Failed to set retry policy fields", e);
        }
    }

    @Test
    @DisplayName("Immediate success — action runs once, no retry")
    void immediateSuccess() {
        AtomicInteger attempts = new AtomicInteger(0);
        AtomicInteger failures = new AtomicInteger(0);

        retryPolicy.executeWithRetry(
            attempts::incrementAndGet,
            failures::incrementAndGet,
            "test"
        );

        assertThat(attempts.get()).isEqualTo(1);
        assertThat(failures.get()).isEqualTo(0);
    }

    @Test
    @DisplayName("Retry on DeliveryException, then succeed on second attempt")
    void retryThenSucceed() {
        AtomicInteger attempts = new AtomicInteger(0);

        retryPolicy.executeWithRetry(
            () -> {
                if (attempts.incrementAndGet() < 2) {
                    throw new DeliveryException("Transient error");
                }
            },
            () -> {},
            "test"
        );

        assertThat(attempts.get()).isEqualTo(2);
    }

    @Test
    @DisplayName("All attempts exhausted — onFinalFailure callback invoked")
    void allAttemptsExhausted_callsOnFinalFailure() {
        AtomicInteger failures = new AtomicInteger(0);
        AtomicInteger attempts = new AtomicInteger(0);

        retryPolicy.executeWithRetry(
            () -> {
                attempts.incrementAndGet();
                throw new DeliveryException("Permanent error");
            },
            failures::incrementAndGet,
            "test"
        );

        assertThat(attempts.get()).isEqualTo(3);
        assertThat(failures.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("Non-DeliveryException stops immediately without retry")
    void unexpectedException_noRetry() {
        AtomicInteger attempts = new AtomicInteger(0);

        retryPolicy.executeWithRetry(
            () -> {
                attempts.incrementAndGet();
                throw new RuntimeException("Unexpected");
            },
            () -> {},
            "test"
        );

        assertThat(attempts.get()).isEqualTo(1);
    }
}
