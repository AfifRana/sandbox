package com.example.order.adapter.out.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.ResourceAccessException;

/**
 * Verifies circuit breaker semantics as used by the catalog client: repeated
 * failures open the circuit, and the open circuit fails fast instead of
 * attempting the call.
 */
class ProductCatalogClientCircuitBreakerTest {

    @Test
    void opensAfterRepeatedFailuresAndFailsFast() {
        // Mirror the production instance config from application.yml
        CircuitBreaker cb = CircuitBreaker.of("testCatalog", io.github.resilience4j.circuitbreaker
                .CircuitBreakerConfig.custom()
                .slidingWindowSize(10)
                .failureRateThreshold(50)
                .minimumNumberOfCalls(5)
                .waitDurationInOpenState(java.time.Duration.ofSeconds(10))
                .build());

        Runnable failingCall = () -> {
            throw new ResourceAccessException("connection refused");
        };

        // CLOSED state: calls go through and record failures
        for (int i = 0; i < 5; i++) {
            assertThatThrownBy(() -> cb.executeRunnable(failingCall))
                    .isInstanceOf(RuntimeException.class);
        }
        assertThat(cb.getState()).isEqualTo(CircuitBreaker.State.OPEN);

        // OPEN state: fails fast without invoking the runnable
        long start = System.nanoTime();
        assertThatThrownBy(() -> cb.executeRunnable(failingCall))
                .isInstanceOf(CallNotPermittedException.class);
        assertThat(System.nanoTime() - start).isLessThan(50_000_000L); // < 50ms — no network attempt
    }
}
