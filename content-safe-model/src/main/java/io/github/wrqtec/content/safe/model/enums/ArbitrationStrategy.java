package io.github.wrqtec.content.safe.model.enums;

/**
 * 多供应商仲裁策略
 *
 * @author L.David
 */
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
