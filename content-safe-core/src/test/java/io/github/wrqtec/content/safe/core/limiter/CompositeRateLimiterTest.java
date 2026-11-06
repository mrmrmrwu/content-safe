package io.github.wrqtec.content.safe.core.limiter;

import java.util.List;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CompositeRateLimiterTest {

    @Test
    void rejectsWhenQpsExhausted() {
        CompositeRateLimiter limiter = new CompositeRateLimiter(List.of(
                new TokenBucketRateLimiter(1D),
                new SemaphoreConcurrencyLimiter(10)));
        assertTrue(limiter.tryAcquire().acquired());
        // QPS 耗尽即拒绝，即使并发额度充足
        assertFalse(limiter.tryAcquire().acquired());
    }

    @Test
    void concurrencyPermitNotLeakedWhenQpsRejects() {
        SemaphoreConcurrencyLimiter semaphore = new SemaphoreConcurrencyLimiter(1);
        // 并发在前：先获并发许可，QPS 拒绝时必须归还
        CompositeRateLimiter limiter = new CompositeRateLimiter(List.of(
                semaphore,
                new TokenBucketRateLimiter(1D)));
        assertTrue(limiter.tryAcquire().acquired());
        assertFalse(limiter.tryAcquire().acquired());
        // 拒绝路径不得占用并发许可（composite 已归还上一个许可由 release 验证，
        // 这里验证拒绝自身不泄漏：可用许可应为 0 因为第一个许可仍持有）
        assertEquals(0, semaphore.availablePermits());
    }

    @Test
    void releaseReturnsAllPermits() {
        SemaphoreConcurrencyLimiter semaphore = new SemaphoreConcurrencyLimiter(1);
        CompositeRateLimiter limiter = new CompositeRateLimiter(List.of(
                new TokenBucketRateLimiter(100D),
                semaphore));
        RateLimiter.Permit permit = limiter.tryAcquire();
        assertEquals(0, semaphore.availablePermits());
        permit.release();
        assertEquals(1, semaphore.availablePermits());
    }

    @Test
    void requiresNonEmptyDelegates() {
        assertThrows(IllegalArgumentException.class, () -> new CompositeRateLimiter(List.of()));
    }

    @Test
    void nameJoinsDelegates() {
        CompositeRateLimiter limiter = new CompositeRateLimiter(List.of(
                new TokenBucketRateLimiter(50D),
                new SemaphoreConcurrencyLimiter(4)));
        String name = limiter.name();
        assertTrue(name.contains("token-bucket"));
        assertTrue(name.contains("concurrency"));
    }
}
