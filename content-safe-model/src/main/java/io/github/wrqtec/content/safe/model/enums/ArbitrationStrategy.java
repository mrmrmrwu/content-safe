package io.github.wrqtec.content.safe.model.enums;

/**
 * 多供应商仲裁策略
 *
 * @author L.David
 * @deprecated 自 1.0.0 发布以来全仓库无任何调用方：仲裁的实际选择走
 *     {@code AuditArbitrationPolicy}（策略由 SPI 实现，名称以
 *     {@code policy.name()} 为准，如 core 内置 {@code FIRST_NON_PASS}），
 *     本枚举不在装配或判定路径上，请勿引用。因 1.0.0 已发布无法物理删除
 *     （二进制兼容门禁），计划 2.0.0 移除
 */
@Deprecated(since = "1.1.0", forRemoval = true)
public enum ArbitrationStrategy {

    /** 任一非 PASS 即采用（从严） */
    FIRST_NON_PASS,
    /** 多数表决 */
    VOTE,
    /** 按供应商优先级取首个有效结果 */
    PRIORITY,
    /** 按供应商权重加权 */
    WEIGHTED
}
