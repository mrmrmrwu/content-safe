package io.github.wrqtec.content.safe.core.limiter;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 组合限流器：QPS 与并发任一拒绝即拒绝。
 * 中途拒绝时已获许可立即归还，不泄漏
 *
 * @author L.David
 */
public class CompositeRateLimiter implements RateLimiter {

    private final List<RateLimiter> delegates;

    /**
     * @param delegates 子限流器，至少一个
     */
    public CompositeRateLimiter(List<RateLimiter> delegates) {
        if (delegates == null || delegates.isEmpty()) {
            throw new IllegalArgumentException("delegates must not be empty");
        }
        this.delegates = List.copyOf(delegates);
    }

    @Override
    public Permit tryAcquire() {
        List<Permit> acquired = new ArrayList<>(delegates.size());
        for (RateLimiter delegate : delegates) {
            Permit permit = delegate.tryAcquire();
            if (!permit.acquired()) {
                releaseAll(acquired);
                return Permit.rejected();
            }
            acquired.add(permit);
        }
        return Permit.acquired(() -> releaseAll(acquired));
    }

    private void releaseAll(List<Permit> permits) {
        for (int i = permits.size() - 1; i >= 0; i--) {
            permits.get(i).release();
        }
    }

    @Override
    public String name() {
        return delegates.stream().map(RateLimiter::name).collect(Collectors.joining("+"));
    }
}
