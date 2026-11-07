package io.github.wrqtec.content.safe.spi;

/**
 * 仲裁策略选择器：按场景解析生效的 {@link AuditArbitrationPolicy}。
 * 返回 null 表示该场景不仲裁（走主备 failover，成本最优）；
 * 返回非 null 时同步审核改为多供应商并行采集 + 仲裁合并（成本 = 全部候选各调用一次）
 *
 * @author L.David
 */
public interface AuditArbitrationSelector {

    /**
     * 解析指定场景生效的仲裁策略
     *
     * @param scene 业务场景 code
     * @return 生效的仲裁策略；null 表示该场景不仲裁
     */
    AuditArbitrationPolicy policyFor(String scene);
}
