package io.github.wrqtec.content.safe.core.degrade;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;

/**
 * 滑动窗口熔断器：窗口内失败率达到阈值即 OPEN，冷却期后进入 HALF_OPEN 放行探测流量，
 * 探测成功关闭，失败重新 OPEN。线程安全。
 *
 * @author L.David
 */
public class SlidingWindowCircuitBreaker implements CircuitBreaker {

    /** 默认滑动窗口大小（调用次数）。 */
    private static final int DEFAULT_WINDOW_SIZE = 20;
    /** 默认失败率阈值（窗口内失败占比达到即熔断）。 */
    private static final double DEFAULT_FAILURE_RATE_THRESHOLD = 0.5D;
    /** 默认最小统计调用数：窗口样本不足时不触发熔断。 */
    private static final int DEFAULT_MIN_CALLS = 10;
    /** 默认 OPEN 冷却时长，到期后进入 HALF_OPEN。 */
    private static final Duration DEFAULT_OPEN_DURATION = Duration.ofSeconds(30);
    /** 默认 HALF_OPEN 探测流量上限。 */
    private static final int DEFAULT_HALF_OPEN_MAX_CALLS = 3;

    private final int windowSize;
    private final double failureRateThreshold;
    private final int minCalls;
    private final Duration openDuration;
    private final int halfOpenMaxCalls;

    private final Deque<Boolean> window;
    private State state = State.CLOSED;
    private long openedAtMillis;
    private int halfOpenInFlight;

    /**
     * 默认参数构造。
     */
    public SlidingWindowCircuitBreaker() {
        this(DEFAULT_WINDOW_SIZE, DEFAULT_FAILURE_RATE_THRESHOLD, DEFAULT_MIN_CALLS,
                DEFAULT_OPEN_DURATION, DEFAULT_HALF_OPEN_MAX_CALLS);
    }

    /**
     * @param windowSize           滑动窗口大小（调用次数）
     * @param failureRateThreshold 失败率阈值，窗口内失败占比达到即 OPEN
     * @param minCalls             最小统计调用数，样本不足不熔断
     * @param openDuration         OPEN 冷却时长，到期进入 HALF_OPEN
     * @param halfOpenMaxCalls     HALF_OPEN 探测流量上限
     */
    public SlidingWindowCircuitBreaker(int windowSize, double failureRateThreshold, int minCalls,
                                       Duration openDuration, int halfOpenMaxCalls) {
        this.windowSize = windowSize;
        this.failureRateThreshold = failureRateThreshold;
        this.minCalls = minCalls;
        this.openDuration = openDuration;
        this.halfOpenMaxCalls = halfOpenMaxCalls;
        this.window = new ArrayDeque<>(windowSize);
    }

    @Override
    public synchronized boolean tryAcquire() {
        switch (state) {
            case CLOSED -> {
                return true;
            }
            case OPEN -> {
                if (System.currentTimeMillis() - openedAtMillis >= openDuration.toMillis()) {
                    state = State.HALF_OPEN;
                    halfOpenInFlight = 0;
                } else {
                    return false;
                }
            }
            default -> {
                // HALF_OPEN 继续向下走
            }
        }
        // HALF_OPEN：限量放行探测流量
        if (halfOpenInFlight >= halfOpenMaxCalls) {
            return false;
        }
        halfOpenInFlight++;
        return true;
    }

    @Override
    public synchronized void onSuccess() {
        if (state == State.HALF_OPEN) {
            halfOpenInFlight--;
            close();
            return;
        }
        record(true);
    }

    @Override
    public synchronized void onFailure() {
        if (state == State.HALF_OPEN) {
            halfOpenInFlight--;
            open();
            return;
        }
        record(false);
        if (window.size() >= minCalls) {
            long failures = window.stream().filter(ok -> !ok).count();
            if ((double) failures / window.size() >= failureRateThreshold) {
                open();
            }
        }
    }

    @Override
    public synchronized State state() {
        return state;
    }

    private void record(boolean success) {
        if (window.size() >= windowSize) {
            window.pollFirst();
        }
        window.addLast(success);
    }

    private void open() {
        state = State.OPEN;
        openedAtMillis = System.currentTimeMillis();
    }

    private void close() {
        state = State.CLOSED;
        window.clear();
    }
}
