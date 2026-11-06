package io.github.wrqtec.content.safe.model.enums;

/**
 * 供应商/产品生命周期。DEPRECATED 起装配告警，EOL 建议迁移，REMOVED 拒绝装配。
 *
 * @author L.David
 */
public enum ProviderLifecycle {

    /** 正常使用中 */
    ACTIVE,
    /** 已废弃，装配时告警 */
    DEPRECATED,
    /** 生命周期终止，建议迁移 */
    EOL,
    /** 已移除，拒绝装配 */
    REMOVED
}
