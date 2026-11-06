package io.github.wrqtec.content.safe.core.limiter;

/**
 * 被拒绝许可单例
 *
 * @author L.David
 */
final class RejectedPermit implements RateLimiter.Permit {

    static final RejectedPermit INSTANCE = new RejectedPermit();

    private RejectedPermit() {
    }

    @Override
    public boolean acquired() {
        return false;
    }

    @Override
    public void release() {
        // 被拒绝许可无需释放
    }
}
