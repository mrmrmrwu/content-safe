package io.github.wrqtec.content.safe.core.security;

import java.util.List;

import io.github.wrqtec.content.safe.api.AuditResult;
import io.github.wrqtec.content.safe.api.RiskHit;
import io.github.wrqtec.content.safe.spi.EvidenceRedactor;

/**
 * 证据脱敏统一入口：事件、日志、落库前调用（遵守 {@link EvidenceRedactor} 契约）。
 * 按 provider 匹配脱敏器，精确匹配优先于 {@link #ANY_PROVIDER} 通配，取首个命中者执行
 *
 * @author L.David
 */
public final class EvidenceRedactors {

    /** 通配所有供应商的 redactor key */
    public static final String ANY_PROVIDER = "*";

    private EvidenceRedactors() {
    }

    /**
     * 对审核结果执行证据脱敏
     *
     * @param redactors 已注册的脱敏器，可为 null
     * @param result    原始审核结果，null 时原样返回
     * @param provider  结果所属供应商 key
     * @return 脱敏后的结果副本；无命中脱敏器时原样返回同一实例
     */
    public static AuditResult redactResult(List<EvidenceRedactor> redactors, AuditResult result, String provider) {
        if (result == null || result.hits().isEmpty() || redactors == null || redactors.isEmpty()) {
            return result;
        }
        EvidenceRedactor redactor = redactors.stream()
                .filter(r -> provider.equals(r.provider()) || ANY_PROVIDER.equals(r.provider()))
                .findFirst()
                .orElse(null);
        if (redactor == null) {
            return result;
        }
        List<RiskHit> redactedHits = result.hits().stream().map(redactor::redact).toList();
        return result.withHits(redactedHits);
    }
}
