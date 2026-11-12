package io.github.wrqtec.content.safe.core.limiter;

import io.github.wrqtec.content.safe.model.OperationLimits;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import static org.junit.jupiter.api.Assertions.assertNull;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RateLimiterRegistryTest {

    @Test
    void lazilyCreatesPerPlanKey() {
        RateLimiterRegistry registry = new RateLimiterRegistry();
        OperationLimits limits = new OperationLimits(null, 100D, null, 0L, 0L, false);
        RateLimiter first = registry.get("aliyun:TextModeration", limits);
        RateLimiter second = registry.get("aliyun:TextModeration", limits);
        assertSame(first, second);
        assertTrue(first.name().contains("token-bucket"));
    }

    @Test
    void undeclaredLimitsShareUnlimited() {
        RateLimiterRegistry registry = new RateLimiterRegistry();
        RateLimiter a = registry.get("p:op1", OperationLimits.unlimited());
        RateLimiter b = registry.get("p:op2", null);
        assertSame(RateLimiter.unlimited(), a);
        assertSame(a, b);
        assertTrue(a.tryAcquire().acquired());
    }

    @Test
    void limiterOfCombinesQpsAndConcurrency() {
        RateLimiter both = RateLimiterRegistry.limiterOf(100D, 4);
        assertTrue(both.name().contains("+"));
        assertTrue(RateLimiterRegistry.limiterOf(null, 4).name().contains("concurrency"));
        assertSame(RateLimiter.unlimited(), RateLimiterRegistry.limiterOf(null, null));
        assertSame(RateLimiter.unlimited(), RateLimiterRegistry.limiterOf(0D, 0));
    }

    @Test
    void snapshotReflectsCreatedLimiters() {
        RateLimiterRegistry registry = new RateLimiterRegistry();
        registry.get("a:op", new OperationLimits(null, 10D, null, 0L, 0L, false));
        registry.get("b:op", new OperationLimits(2, null, null, 0L, 0L, false));
        assertEquals(2, registry.snapshot().size());
        assertFalse(registry.snapshot().containsKey("c:op"));
    }
    @Test
    void nullLimiterFactoryFailsFast() {
        // javadoc 承诺 get() 永不为 null：工厂返回 null 必须立即抛出，而不是污染注册表后返回 null
        RateLimiterRegistry registry = new RateLimiterRegistry((key, limits) -> null);
        assertThrows(IllegalStateException.class,
                () -> registry.get("p:op", OperationLimits.unlimited()));
        assertNull(registry.snapshot().get("p:op"));
    }

}
