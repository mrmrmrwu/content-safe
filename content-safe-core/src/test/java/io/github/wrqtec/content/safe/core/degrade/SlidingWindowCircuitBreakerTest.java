package io.github.wrqtec.content.safe.core.degrade;

import java.time.Duration;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SlidingWindowCircuitBreakerTest {

    @Test
    void opensWhenFailureRateExceeded() {
        SlidingWindowCircuitBreaker breaker = new SlidingWindowCircuitBreaker(
                4, 0.5D, 4, Duration.ofMinutes(10), 1);
        assertTrue(breaker.tryAcquire());
        breaker.onFailure();
        assertTrue(breaker.tryAcquire());
        breaker.onFailure();
        assertTrue(breaker.tryAcquire());
        breaker.onSuccess();
        assertTrue(breaker.tryAcquire());
        breaker.onFailure();

        assertEquals(CircuitBreaker.State.OPEN, breaker.state());
        assertFalse(breaker.tryAcquire());
    }

    @Test
    void halfOpenProbeClosesOnSuccess() throws InterruptedException {
        SlidingWindowCircuitBreaker breaker = new SlidingWindowCircuitBreaker(
                2, 0.5D, 2, Duration.ofMillis(50), 1);
        breaker.tryAcquire();
        breaker.onFailure();
        breaker.tryAcquire();
        breaker.onFailure();
        assertEquals(CircuitBreaker.State.OPEN, breaker.state());

        Thread.sleep(60);
        assertTrue(breaker.tryAcquire());
        assertEquals(CircuitBreaker.State.HALF_OPEN, breaker.state());
        breaker.onSuccess();
        assertEquals(CircuitBreaker.State.CLOSED, breaker.state());
    }

    @Test
    void halfOpenFailureReopens() throws InterruptedException {
        SlidingWindowCircuitBreaker breaker = new SlidingWindowCircuitBreaker(
                2, 0.5D, 2, Duration.ofMillis(50), 1);
        breaker.tryAcquire();
        breaker.onFailure();
        breaker.tryAcquire();
        breaker.onFailure();

        Thread.sleep(60);
        assertTrue(breaker.tryAcquire());
        breaker.onFailure();
        assertEquals(CircuitBreaker.State.OPEN, breaker.state());
        assertFalse(breaker.tryAcquire());
    }

    @Test
    void belowMinCallsNeverOpens() {
        SlidingWindowCircuitBreaker breaker = new SlidingWindowCircuitBreaker(
                10, 0.5D, 10, Duration.ofMinutes(10), 1);
        for (int i = 0; i < 5; i++) {
            breaker.tryAcquire();
            breaker.onFailure();
        }
        assertEquals(CircuitBreaker.State.CLOSED, breaker.state());
    }
}
