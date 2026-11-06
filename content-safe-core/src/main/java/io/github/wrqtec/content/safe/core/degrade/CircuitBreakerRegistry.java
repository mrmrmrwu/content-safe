package io.github.wrqtec.content.safe.core.degrade;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Supplier;

/**
 * 熔断器注册表：按 planKey 懒加载，线程安全
 *
 * @author L.David
 */
public class CircuitBreakerRegistry {

    private final ConcurrentMap<String, CircuitBreaker> breakers = new ConcurrentHashMap<>();

    private final Supplier<CircuitBreaker> factory;

    /**
     * 默认构造：使用 {@link SlidingWindowCircuitBreaker} 默认参数
     */
    public CircuitBreakerRegistry() {
        this(SlidingWindowCircuitBreaker::new);
    }

    /**
     * @param factory 熔断器工厂，按 planKey 懒加载时调用
     */
    public CircuitBreakerRegistry(Supplier<CircuitBreaker> factory) {
        this.factory = factory;
    }

    /**
     * 获取（或懒创建）指定 planKey 的熔断器
     *
     * @param planKey 供应商操作键（provider:operation）
     * @return 熔断器，永不为 null
     */
    public CircuitBreaker get(String planKey) {
        return breakers.computeIfAbsent(planKey, k -> factory.get());
    }

    /**
     * 当前所有熔断器状态快照（用于健康检查与监控，不触发任何远程调用）
     *
     * @return planKey → 熔断状态
     */
    public Map<String, CircuitBreaker.State> snapshot() {
        Map<String, CircuitBreaker.State> out = new LinkedHashMap<>();
        breakers.forEach((key, breaker) -> out.put(key, breaker.state()));
        return out;
    }
}
