package io.github.wrqtec.content.safe.api;

import java.util.List;
import java.util.Map;

import io.github.wrqtec.content.safe.api.enums.ConfidenceScale;
import io.github.wrqtec.content.safe.api.enums.HitSource;

/**
 * 单条风险命中详情。不可变。
 *
 * <p>{@code evidence} 必须已经过 {@code EvidenceRedactor} 脱敏/截断；
 * {@code raw} 默认不进入日志与审计事件正文。</p>
 *
 * @param labelPath       归一化标签路径，如 {@code porn/sexual_accusation}
 * @param labels          供应商原始标签链
 * @param confidence      置信度，取值范围由 {@code confidenceScale} 标明
 * @param confidenceScale 置信度分制
 * @param source          命中来源
 * @param evidence        脱敏后的命中证据
 * @param coordinates     图片命中区域坐标（供应商原始格式）
 * @param raw             供应商原始命中片段（默认不序列化、不进日志）
 *
 * @author L.David
 */
public record RiskHit(
        String labelPath,
        List<String> labels,
        Double confidence,
        ConfidenceScale confidenceScale,
        HitSource source,
        String evidence,
        String coordinates,
        Map<String, Object> raw) {

    public RiskHit {
        labels = labels == null ? List.of() : List.copyOf(labels);
        raw = raw == null ? Map.of() : Map.copyOf(raw);
    }
}
