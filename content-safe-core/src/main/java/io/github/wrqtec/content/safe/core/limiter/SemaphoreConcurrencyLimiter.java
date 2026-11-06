package io.github.wrqtec.content.safe.core.limiter;

import java.util.concurrent.Semaphore;

/**
 * 并发限流器：公平信号量，非阻塞获取。
 * 获准许可必须在 finally 中 {@link Permit#release()}
 *
 * @author L.David
 */
public class SemaphoreConcurrencyLimiter implements RateLimiter {

    private final int maxConcurrency;

    private final Semaphore semaphore;

    /**
     * @param maxConcurrency 最大并发数，必须大于 0
     */
    public SemaphoreConcurrencyLimiter(int maxConcurrency) {
        if (maxConcurrency <= 0) {
            throw new IllegalArgumentException("maxConcurrency must be positive: " + maxConcurrency);
        }
        this.maxConcurrency = maxConcurrency;
        this.semaphore = new Semaphore(maxConcurrency, true);
    }

    @Override
    public Permit tryAcquire() {
        if (semaphore.tryAcquire()) {
            return Permit.acquired(semaphore::release);
        }
        return Permit.rejected();
    }

    /**
     * 当前可用许可数（观测用）
     *
     * @return 可用许可数
     */
    public int availablePermits() {
        return semaphore.availablePermits();
    }

    @Override
    public String name() {
        return "concurrency(max=" + maxConcurrency + ")";
    }
}
