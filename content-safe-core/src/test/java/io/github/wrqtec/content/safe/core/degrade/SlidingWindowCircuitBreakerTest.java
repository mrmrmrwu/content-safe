package io.github.wrqtec.content.safe.core.degrade;

import java.time.Duration;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
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
    @Test
    void rejectsInvalidConstructorArguments() {
        // 坏配置必须启动期失败，不能延迟到首次熔断冷却才 NPE / 半开永不放行
        assertThrows(IllegalArgumentException.class,
                () -> new SlidingWindowCircuitBreaker(0, 0.5D, 4, Duration.ofMinutes(1), 1));
        assertThrows(IllegalArgumentException.class,
                () -> new SlidingWindowCircuitBreaker(4, 0D, 4, Duration.ofMinutes(1), 1));
        assertThrows(IllegalArgumentException.class,
                () -> new SlidingWindowCircuitBreaker(4, 1.5D, 4, Duration.ofMinutes(1), 1));
        assertThrows(IllegalArgumentException.class,
                () -> new SlidingWindowCircuitBreaker(4, 0.5D, 0, Duration.ofMinutes(1), 1));
        assertThrows(IllegalArgumentException.class,
                () -> new SlidingWindowCircuitBreaker(4, 0.5D, 4, null, 1));
        assertThrows(IllegalArgumentException.class,
                () -> new SlidingWindowCircuitBreaker(4, 0.5D, 4, Duration.ZERO, 1));
        assertThrows(IllegalArgumentException.class,
                () -> new SlidingWindowCircuitBreaker(4, 0.5D, 4, Duration.ofMinutes(1), 0));
    }

    @Test
    void acceptsValidBoundaryArguments() {
        new SlidingWindowCircuitBreaker(1, 1D, 1, Duration.ofNanos(1), 1);
    }

}
