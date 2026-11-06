package io.github.wrqtec.content.safe.api.enums;

/**
 * 归一化后的审核建议。各 provider 的原始结果必须归一到此枚举。
 *
 * <ul>
 *     <li>{@link #PASS} 放行</li>
 *     <li>{@link #REVIEW} 转人工复审</li>
 *     <li>{@link #REJECT} 拦截</li>
 *     <li>{@link #ERROR} 审核未完成，按业务 fail-safe 策略处理，不得当作 PASS</li>
 * </ul>
 *
 * @author L.David
 */
public enum RiskSuggestion {

    /** 放行 */
    PASS,
    /** 转人工复审 */
    REVIEW,
    /** 拦截 */
    REJECT,
    /** 审核未完成，按业务 fail-safe 策略处理，不得当作 PASS */
    ERROR
}
