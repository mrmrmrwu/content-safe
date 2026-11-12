package io.github.wrqtec.content.safe.core.degrade;

import java.time.Duration;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CircuitBreakerRegistryTest {

    @Test
    void lazilyCreatesPerPlanKey() {
        CircuitBreakerRegistry registry = new CircuitBreakerRegistry();
        CircuitBreaker first = registry.get("aliyun:TextModeration");
        CircuitBreaker second = registry.get("aliyun:TextModeration");
        assertSame(first, second);
    }

    @Test
    void nullBreakerFactoryFailsFast() {
        // javadoc 承诺 get() 永不为 null：工厂返回 null 必须立即抛出，而不是污染注册表后返回 null
        CircuitBreakerRegistry registry = new CircuitBreakerRegistry(() -> null);
        assertThrows(IllegalStateException.class, () -> registry.get("p:op"));
        assertNull(registry.snapshot().get("p:op"));
    }

    @Test
    void customFactoryIsHonored() {
        CircuitBreaker custom = new SlidingWindowCircuitBreaker(
                4, 0.5D, 4, Duration.ofMinutes(1), 1);
        CircuitBreakerRegistry registry = new CircuitBreakerRegistry(() -> custom);
        assertSame(custom, registry.get("p:op"));
    }
}
