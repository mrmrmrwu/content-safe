package io.github.wrqtec.content.safe.core.limiter;

import java.util.Locale;

/**
 * 令牌桶 QPS 限流器（纯 JDK）。
 * 容量 = 速率（突发容忍 1 秒），按 nanoTime 懒补充
 *
 * @author L.David
 */
public class TokenBucketRateLimiter implements RateLimiter {

    private static final long NANOS_PER_SECOND = 1_000_000_000L;

    private static final double MIN_PERMITS_PER_SECOND = 0.000_001D;

    private final double permitsPerSecond;

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
        this.storedPermits = permitsPerSecond;
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
        storedPermits = Math.min(permitsPerSecond, storedPermits + newPermits);
        lastRefillNanos = now;
    }

    @Override
    public String name() {
        return String.format(Locale.ROOT, "token-bucket(qps=%s)", permitsPerSecond);
    }
}
