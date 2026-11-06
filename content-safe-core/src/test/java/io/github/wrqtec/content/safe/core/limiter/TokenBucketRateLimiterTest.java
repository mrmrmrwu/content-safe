package io.github.wrqtec.content.safe.core.limiter;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TokenBucketRateLimiterTest {

    @Test
    void acquiresWithinRate() {
        TokenBucketRateLimiter limiter = new TokenBucketRateLimiter(10D);
        for (int i = 0; i < 10; i++) {
            assertTrue(limiter.tryAcquire().acquired());
        }
    }

    @Test
    void rejectsBeyondCapacity() {
        TokenBucketRateLimiter limiter = new TokenBucketRateLimiter(5D);
        for (int i = 0; i < 5; i++) {
            limiter.tryAcquire();
        }
        assertFalse(limiter.tryAcquire().acquired());
    }

    @Test
    void refillsOverTime() throws InterruptedException {
        TokenBucketRateLimiter limiter = new TokenBucketRateLimiter(100D);
        for (int i = 0; i < 100; i++) {
            limiter.tryAcquire();
        }
        assertFalse(limiter.tryAcquire().acquired());
        // 100 QPS → 20ms 约补充 2 个许可
        Thread.sleep(30L);
        assertTrue(limiter.tryAcquire().acquired());
    }

    @Test
    void releaseIsNoOpForQpsPermit() {
        TokenBucketRateLimiter limiter = new TokenBucketRateLimiter(1D);
        RateLimiter.Permit permit = limiter.tryAcquire();
        permit.release();
        // QPS 许可 acquire 即消费，release 不返还额度
        assertFalse(limiter.tryAcquire().acquired());
    }

    @Test
    void rejectsNonPositiveRate() {
        assertThrows(IllegalArgumentException.class, () -> new TokenBucketRateLimiter(0D));
        assertThrows(IllegalArgumentException.class, () -> new TokenBucketRateLimiter(-1D));
    }

    @Test
    void nameContainsQps() {
        assertTrue(new TokenBucketRateLimiter(100D).name().contains("100.0"));
    }
}
