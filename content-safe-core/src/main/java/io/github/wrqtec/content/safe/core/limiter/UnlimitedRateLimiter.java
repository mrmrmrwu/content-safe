package io.github.wrqtec.content.safe.core.limiter;

/**
 * 无限制限流器单例：始终放行
 *
 * @author L.David
 */
final class UnlimitedRateLimiter implements RateLimiter {

    static final UnlimitedRateLimiter INSTANCE = new UnlimitedRateLimiter();

    /** release 为纯空操作且无内部状态：不走 {@link Permit#acquired(Runnable)}，避免共享 synchronized 实例成为全局锁 */
    private static final RateLimiter.Permit PERMIT = new NoopPermit();

    private UnlimitedRateLimiter() {
    }

    @Override
    public Permit tryAcquire() {
        return PERMIT;
    }

    @Override
    public String name() {
        return "unlimited";
    }

    private static final class NoopPermit implements RateLimiter.Permit {

        @Override
        public boolean acquired() {
            return true;
        }

        @Override
        public void release() {
            // unlimited 许可不持有任何资源
        }
    }
}
