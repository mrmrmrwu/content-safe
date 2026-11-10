package io.github.wrqtec.content.safe.spi;

/**
 * 仲裁策略选择器：按场景解析生效的 {@link AuditArbitrationPolicy}。
 * 返回 null 表示该场景不仲裁（走主备 failover，成本最优）；
 * 返回非 null 时同步审核改为多供应商并行采集 + 仲裁合并（成本 = 全部候选各调用一次）
 *
 * <p>实现契约：本方法在每次同步审核热路径上按场景调用，实现应保持低开销——
 * 建议启动时预构建策略对象并返回共享实例（配置变更时整体替换选择器实例），
 * 不要在方法内现构造策略对象
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
