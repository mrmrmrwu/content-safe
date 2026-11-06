package io.github.wrqtec.content.safe.core.degrade;

/**
 * 熔断器：按 {@code planKey}（provider:operation）粒度隔离
 *
 * @author L.David
 */
public interface CircuitBreaker {

    /**
     * 尝试获取调用许可
     *
     * @return true 允许调用；false 熔断中
     */
    boolean tryAcquire();

    /**
     * 记录成功
     */
    void onSuccess();

    /**
     * 记录失败
     */
    void onFailure();

    /**
     * 当前状态
     */
    State state();

    enum State {
        /** 关闭：正常放行 */
        CLOSED,
        /** 打开：熔断中，直接拒绝 */
        OPEN,
        /** 半开：冷却期后限量放行探测流量 */
        HALF_OPEN
    }
}
