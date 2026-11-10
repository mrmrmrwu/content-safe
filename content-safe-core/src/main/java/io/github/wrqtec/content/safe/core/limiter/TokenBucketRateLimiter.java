package io.github.wrqtec.content.safe.core.limiter;

import java.util.Locale;

/**
 * 令牌桶 QPS 限流器（纯 JDK）。
 * 容量 = max(1, 速率)（突发至少容忍 1 个许可，速率 &lt; 1 时按 1 秒一个放行），按 nanoTime 懒补充
 *
 * @author L.David
 */
public class TokenBucketRateLimiter implements RateLimiter {

    private static final long NANOS_PER_SECOND = 1_000_000_000L;

    private static final double MIN_PERMITS_PER_SECOND = 0.000_001D;

    /** 容量下限：低于 1 时桶永远凑不足单次许可，会导致全部拒绝 */
    private static final double MIN_CAPACITY = 1.0D;

    private final double permitsPerSecond;

    private final double capacity;

    private double storedPermits;

    private long lastRefillNanos;

    /**
     * @param permitsPerSecond 每秒许可数，必须大于 0
     */
    public TokenBucketRateLimiter(double permitsPerSecond) {
        if (permitsPerSecond < MIN_PERMITS_PER_SECOND) {
            throw new IllegalArgumentException("permitsPerSecond must be positive: " + permitsPerSecond);
        }
        this.permitsPerSecond = permitsPerSecond;
        this.capacity = Math.max(MIN_CAPACITY, permitsPerSecond);
        this.storedPermits = this.capacity;
        this.lastRefillNanos = System.nanoTime();
    }

    @Override
    public synchronized Permit tryAcquire() {
        refill();
        if (storedPermits >= 1.0D) {
            storedPermits -= 1.0D;
            // QPS 许可 acquire 即消费，release 为空操作
            return Permit.acquired(() -> {
            });
        }
        return Permit.rejected();
    }

    private void refill() {
        long now = System.nanoTime();
        long elapsed = now - lastRefillNanos;
        if (elapsed <= 0L) {
            return;
        }
        double newPermits = (double) elapsed / NANOS_PER_SECOND * permitsPerSecond;
        storedPermits = Math.min(capacity, storedPermits + newPermits);
        lastRefillNanos = now;
    }

    @Override
    public String name() {
        return String.format(Locale.ROOT, "token-bucket(qps=%s)", permitsPerSecond);
    }
}
