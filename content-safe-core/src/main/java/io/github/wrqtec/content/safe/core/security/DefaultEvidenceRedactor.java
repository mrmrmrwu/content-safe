package io.github.wrqtec.content.safe.core.security;

import io.github.wrqtec.content.safe.api.RiskHit;
import io.github.wrqtec.content.safe.spi.EvidenceRedactor;

/**
 * 默认证据脱敏：命中证据只保留首尾各 2 个字符，中间以 {@code ***} 代替
 * 保证事件/日志/落库中不出现用户内容原文
 *
 * @author L.David
 */
public class DefaultEvidenceRedactor implements EvidenceRedactor {

    /** 通配 provider：匹配任意供应商 */
    private static final String ANY_PROVIDER = "*";
    /** 脱敏掩码 */
    private static final String MASK = "***";
    /** 证据两端各保留的可见字符数 */
    private static final int VISIBLE_EDGE_CHARS = 2;
    /** 证据长度不超过该值时整体打码 */
    private static final int MASK_ONLY_MAX_LENGTH = 2 * VISIBLE_EDGE_CHARS;

    @Override
    public String provider() {
        return ANY_PROVIDER;
    }

    @Override
    public RiskHit redact(RiskHit hit) {
        if (hit == null || hit.evidence() == null) {
            return hit;
        }
        return new RiskHit(hit.labelPath(), hit.labels(), hit.confidence(), hit.confidenceScale(),
                hit.source(), mask(hit.evidence()), hit.coordinates(), hit.raw());
    }

    /**
     * 证据打码：长度不超过 {@link #MASK_ONLY_MAX_LENGTH} 时整体替换为掩码，
     * 否则保留首尾各 {@link #VISIBLE_EDGE_CHARS} 个字符
     *
     * @param evidence 原始证据，非 null
     * @return 脱敏后的证据
     */
    static String mask(String evidence) {
        int len = evidence.length();
        if (len <= MASK_ONLY_MAX_LENGTH) {
            return MASK;
        }
        return evidence.substring(0, VISIBLE_EDGE_CHARS) + MASK + evidence.substring(len - VISIBLE_EDGE_CHARS);
    }
}
