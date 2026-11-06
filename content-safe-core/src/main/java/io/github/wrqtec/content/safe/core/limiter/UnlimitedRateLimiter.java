package io.github.wrqtec.content.safe.core.limiter;

/**
 * 无限制限流器单例：始终放行
 *
 * @author L.David
 */
final class UnlimitedRateLimiter implements RateLimiter {

    static final UnlimitedRateLimiter INSTANCE = new UnlimitedRateLimiter();

    private static final RateLimiter.Permit PERMIT = Permit.acquired(() -> {
    });

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
}
