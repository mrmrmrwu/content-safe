package io.github.wrqtec.content.safe.core.limiter;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SemaphoreConcurrencyLimiterTest {

    @Test
    void rejectsWhenExhausted() {
        SemaphoreConcurrencyLimiter limiter = new SemaphoreConcurrencyLimiter(2);
        RateLimiter.Permit first = limiter.tryAcquire();
        RateLimiter.Permit second = limiter.tryAcquire();
        assertTrue(first.acquired());
        assertTrue(second.acquired());
        assertFalse(limiter.tryAcquire().acquired());
        assertEquals(0, limiter.availablePermits());
    }

    @Test
    void recoversAfterRelease() {
        SemaphoreConcurrencyLimiter limiter = new SemaphoreConcurrencyLimiter(1);
        RateLimiter.Permit permit = limiter.tryAcquire();
        assertFalse(limiter.tryAcquire().acquired());
        permit.release();
        assertEquals(1, limiter.availablePermits());
        assertTrue(limiter.tryAcquire().acquired());
    }

    @Test
    void releaseIsIdempotent() {
        SemaphoreConcurrencyLimiter limiter = new SemaphoreConcurrencyLimiter(1);
        RateLimiter.Permit permit = limiter.tryAcquire();
        permit.release();
        permit.release();
        assertEquals(1, limiter.availablePermits());
    }

    @Test
    void rejectedPermitReleaseIsSafe() {
        SemaphoreConcurrencyLimiter limiter = new SemaphoreConcurrencyLimiter(1);
        limiter.tryAcquire();
        RateLimiter.Permit rejected = limiter.tryAcquire();
        assertFalse(rejected.acquired());
        rejected.release();
        assertEquals(0, limiter.availablePermits());
    }

    @Test
    void rejectsNonPositiveMaxConcurrency() {
        assertThrows(IllegalArgumentException.class, () -> new SemaphoreConcurrencyLimiter(0));
    }
}
