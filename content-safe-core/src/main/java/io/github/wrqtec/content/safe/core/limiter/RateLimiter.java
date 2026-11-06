package io.github.wrqtec.content.safe.core.limiter;

/**
 * 限流器：按 {@code planKey}（provider:operation）粒度隔离。
 * 与熔断器对称：治理 SPI 放在 core 内部，替换入口为
 * {@link RateLimiterRegistry} 工厂 + starter {@code @ConditionalOnMissingBean}
 *
 * @author L.David
 */
public interface RateLimiter {

    /**
     * 尝试获取调用许可（非阻塞）
     *
     * @return 许可；{@link Permit#acquired()} 为 false 表示被限流
     */
    Permit tryAcquire();

    /**
     * 限流器名称（观测用，低基数）
     *
     * @return 如 {@code token-bucket(qps=100.0)}
     */
    String name();

    /**
     * 无限制限流器（未声明额度时共享）
     *
     * @return 始终放行的限流器单例
     */
    static RateLimiter unlimited() {
        return UnlimitedRateLimiter.INSTANCE;
    }

    /**
     * 调用许可。QPS 许可 acquire 即消费（release 为空操作）；
     * 并发许可必须在 finally 中 release
     */
    interface Permit extends AutoCloseable {

        /**
         * 是否获准
         *
         * @return true 已获准
         */
        boolean acquired();

        /**
         * 释放许可（未获准或 QPS 许可时为空操作，幂等）
         */
        void release();

        @Override
        default void close() {
            release();
        }

        /**
         * 构造已获准许可
         *
         * @param onRelease release 回调（并发许可用于归还信号量），不允许 null
         * @return 已获准许可
         */
        static Permit acquired(Runnable onRelease) {
            return new Permit() {
                private boolean released;

                @Override
                public boolean acquired() {
                    return true;
                }

                @Override
                public synchronized void release() {
                    if (!released) {
                        released = true;
                        onRelease.run();
                    }
                }
            };
        }

        /**
         * 构造被拒绝许可
         *
         * @return 被拒绝许可
         */
        static Permit rejected() {
            return RejectedPermit.INSTANCE;
        }
    }
}
