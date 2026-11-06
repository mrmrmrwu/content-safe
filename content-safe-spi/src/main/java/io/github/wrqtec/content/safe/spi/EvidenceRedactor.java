package io.github.wrqtec.content.safe.spi;

import io.github.wrqtec.content.safe.api.RiskHit;

/**
 * 证据脱敏。在事件、日志、落库前调用。禁止把原文片段直接透出
 *
 * @author L.David
 */
public interface EvidenceRedactor {

    /**
     * 供应商 key（全局则返回 "*"）
     *
     * @return 供应商 key 或 {@code *}
     */
    String provider();

    /**
     * 脱敏。输入原始 RiskHit，输出脱敏后的副本
     *
     * @param hit 原始风险命中记录，不得为 null
     * @return 脱敏后的风险命中副本
     */
    RiskHit redact(RiskHit hit);
}
