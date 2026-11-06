package io.github.wrqtec.content.safe.core.limiter;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.BiFunction;

import io.github.wrqtec.content.safe.model.OperationLimits;

/**
 * 限流器注册表：按 planKey 懒加载，线程安全。
 * 额度默认取 {@link OperationLimits} 声明（qps / maxConcurrency），工厂可叠加配置覆盖
 *
 * @author L.David
 */
public class RateLimiterRegistry {

    private final ConcurrentMap<String, RateLimiter> limiters = new ConcurrentHashMap<>();

    private final BiFunction<String, OperationLimits, RateLimiter> factory;

    /**
     * 默认构造：按 plan 声明组装（qps → 令牌桶，maxConcurrency → 信号量，均无 → unlimited）
     */
    public RateLimiterRegistry() {
        this((planKey, limits) -> RateLimiterRegistry.defaultLimiter(limits));
    }

    /**
     * @param factory 限流器工厂，按 planKey 懒加载时调用（planKey, plan 声明额度）
     */
    public RateLimiterRegistry(BiFunction<String, OperationLimits, RateLimiter> factory) {
        this.factory = factory;
    }

    /**
     * 按 plan 声明组装默认限流器
     *
     * @param limits plan 声明额度，null 视为未声明
     * @return 限流器（未声明额度时返回共享 unlimited）
     */
    public static RateLimiter defaultLimiter(OperationLimits limits) {
        return limiterOf(limits == null ? null : limits.qps(),
                limits == null ? null : limits.maxConcurrency());
    }

    /**
     * 按额度组装限流器（starter 配置覆盖与 plan 声明共用）
     *
     * @param qps            每秒许可数，null 或不大于 0 表示不限
     * @param maxConcurrency 并发上限，null 或不大于 0 表示不限
     * @return 限流器（两项均不限时返回共享 unlimited）
     */
    public static RateLimiter limiterOf(Double qps, Integer maxConcurrency) {
        boolean hasQps = qps != null && qps > 0D;
        boolean hasConcurrency = maxConcurrency != null && maxConcurrency > 0;
        if (!hasQps && !hasConcurrency) {
            return RateLimiter.unlimited();
        }
        if (hasQps && hasConcurrency) {
            return new CompositeRateLimiter(List.of(
                    new TokenBucketRateLimiter(qps),
                    new SemaphoreConcurrencyLimiter(maxConcurrency)));
        }
        return hasQps
                ? new TokenBucketRateLimiter(qps)
                : new SemaphoreConcurrencyLimiter(maxConcurrency);
    }

    /**
     * 获取（或懒创建）指定 planKey 的限流器。
     * 同一 planKey 首次使用的 limits 生效，后续调用以首次创建的限流器为准
     *
     * @param planKey 供应商操作键（provider:operation）
     * @param limits plan 声明额度
     * @return 限流器，永不为 null
     */
    public RateLimiter get(String planKey, OperationLimits limits) {
        return limiters.computeIfAbsent(planKey, k -> factory.apply(k, limits));
    }

    /**
     * 当前所有限流器快照（用于健康检查与监控，不触发任何远程调用）
     *
     * @return planKey → 限流器名称
     */
    public Map<String, String> snapshot() {
        Map<String, String> out = new LinkedHashMap<>();
        limiters.forEach((key, limiter) -> out.put(key, limiter.name()));
        return out;
    }
}
