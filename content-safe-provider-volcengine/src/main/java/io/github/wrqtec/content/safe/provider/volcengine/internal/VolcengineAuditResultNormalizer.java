package io.github.wrqtec.content.safe.provider.volcengine.internal;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.volcengine.model.response.ImageRiskDetectionResponse;
import com.volcengine.model.response.ResponseMetadata;
import com.volcengine.model.response.TextRiskResponse;
import io.github.wrqtec.content.safe.api.AuditResult;
import io.github.wrqtec.content.safe.api.RiskHit;
import io.github.wrqtec.content.safe.api.enums.AuditSource;
import io.github.wrqtec.content.safe.api.enums.HitSource;
import io.github.wrqtec.content.safe.api.enums.RiskSuggestion;
import io.github.wrqtec.content.safe.api.exception.ProviderCallException;
import io.github.wrqtec.content.safe.model.ProviderPlan;
import io.github.wrqtec.content.safe.provider.volcengine.VolcengineProviderConstants;
import io.github.wrqtec.content.safe.spi.AuditResultNormalizer;
import lombok.extern.slf4j.Slf4j;

/**
 * 火山引擎内容安全响应归一化（铁律 §5.4：provider 返回结构只允许由
 * Normalizer 翻译）
 *
 * <p>decision 映射：pass → PASS，suspect → REVIEW，reject → REJECT；
 * 缺失/未知值保守回退 REVIEW（交人工复核）。decision 完整枚举以官方
 * 文档为准（来源见 docs/provider-feature-matrix.md）
 *
 * <p>安全约束（铁律 §5.6/§9）：raw 只保留 {RequestId, Service}；
 * 文本命中词（contexts.matchedWords）为用户内容片段，只进 hit.raw；
 * OCR 文本（data.ocrText）与图片帧 URL（frames）为用户内容/签名 URL，
 * 一律丢弃，不进 evidence/raw/日志
 *
 * @author L.David
 */
@Slf4j
public class VolcengineAuditResultNormalizer implements AuditResultNormalizer {

    /** 归一化上下文键 */
    public static final String CONTEXT_SERVICE = "service";
    public static final String CONTEXT_DATA_ID = "dataId";
    public static final String CONTEXT_COST = "cost";

    /** 标签路径连接符 */
    private static final String LABEL_PATH_SEPARATOR = "/";

    @Override
    public String provider() {
        return VolcengineProviderConstants.PROVIDER;
    }

    /**
     * 构造归一化上下文
     *
     * @param plan   操作计划（提供 service 标识）
     * @param dataId 业务侧数据唯一标识，可为 null
     * @param cost   调用耗时
     * @return 上下文
     */
    public static Map<String, Object> context(ProviderPlan plan, String dataId, Duration cost) {
        Map<String, Object> context = new LinkedHashMap<>();
        context.put(CONTEXT_SERVICE, plan.service());
        context.put(CONTEXT_DATA_ID, dataId);
        context.put(CONTEXT_COST, cost);
        return context;
    }

    @Override
    public AuditResult normalize(Object rawResponse, Map<String, Object> context) {
        if (rawResponse instanceof TextRiskResponse response) {
            return normalizeText(response, context);
        }
        if (rawResponse instanceof ImageRiskDetectionResponse response) {
            return normalizeImage(response, context);
        }
        throw new ProviderCallException("unsupported volcengine raw response type: "
                + (rawResponse == null ? "null" : rawResponse.getClass().getName()));
    }

    private AuditResult normalizeText(TextRiskResponse response, Map<String, Object> context) {
        assertNoMetadataError(response.getResponseMetadata(), context);
        TextRiskResponse.RiskDetectionResult result = response.getResult();
        TextRiskResponse.DecisionData data = result == null ? null : result.getData();
        if (result == null || data == null) {
            throw new ProviderCallException("volcengine text risk returned empty result: requestId="
                    + (result == null ? "" : nullSafe(result.getRequestId())));
        }
        AuditResult.Builder builder = baseBuilder(result.getRequestId(), context);
        builder.suggestion(fromDecision(data.getDecision()));
        List<RiskHit> hits = new ArrayList<>();
        appendTextHits(hits, data.getResult());
        return builder.hits(hits).build();
    }

    private AuditResult normalizeImage(ImageRiskDetectionResponse response, Map<String, Object> context) {
        assertNoMetadataError(response.getResponseMetadata(), context);
        ImageRiskDetectionResponse.ImageDetectionResult result = response.getResult();
        ImageRiskDetectionResponse.ImageDetectionData data = result == null ? null : result.getData();
        if (result == null || data == null) {
            throw new ProviderCallException("volcengine image content risk returned empty result: requestId="
                    + (result == null ? "" : nullSafe(result.getRequestId())));
        }
        AuditResult.Builder builder = baseBuilder(result.getRequestId(), context);
        builder.suggestion(fromDecision(data.getDecision()));
        List<RiskHit> hits = new ArrayList<>();
        appendImageHits(hits, data.getResults());
        return builder.hits(hits).build();
    }

    /**
     * 文本命中归一：label/subLabel 进 labels；contexts[].matchedWords 是
     * 命中的用户内容片段，只进 hit.raw（raw 按约定不进日志/审计正文）
     */
    private void appendTextHits(List<RiskHit> hits, List<TextRiskResponse.Result> results) {
        if (results == null) {
            return;
        }
        for (TextRiskResponse.Result item : results) {
            if (item == null) {
                continue;
            }
            List<String> labels = labelsOf(item.getLabel(), item.getSubLabel());
            if (labels.isEmpty()) {
                continue;
            }
            Map<String, Object> raw = new LinkedHashMap<>();
            if (item.getContexts() != null && !item.getContexts().isEmpty()) {
                List<String> matchedWords = item.getContexts().stream()
                        .filter(context -> context != null && context.getMatchedWords() != null)
                        .flatMap(context -> context.getMatchedWords().stream())
                        .filter(word -> word != null && !word.isBlank())
                        .toList();
                if (!matchedWords.isEmpty()) {
                    raw.put("matchedWords", matchedWords);
                }
                String libName = item.getContexts().get(0) == null ? null : item.getContexts().get(0).getLibName();
                if (libName != null && !libName.isBlank()) {
                    raw.put("libName", libName);
                }
            }
            hits.add(new RiskHit(
                    String.join(LABEL_PATH_SEPARATOR, labels),
                    labels,
                    null,
                    null,
                    HitSource.MACHINE,
                    null, null, raw));
        }
    }

    /**
     * 图片命中归一：label/subLabel 进 labels；detail（标签明细字符串）
     * 进 hit.raw；ocrText/frames 不透传
     */
    private void appendImageHits(List<RiskHit> hits, List<ImageRiskDetectionResponse.ImageLabelResult> results) {
        if (results == null) {
            return;
        }
        for (ImageRiskDetectionResponse.ImageLabelResult item : results) {
            if (item == null) {
                continue;
            }
            List<String> labels = labelsOf(item.getLabel(), item.getSubLabel());
            if (labels.isEmpty()) {
                continue;
            }
            Map<String, Object> raw = new LinkedHashMap<>();
            if (item.getDetail() != null && !item.getDetail().isEmpty()) {
                raw.put("details", List.copyOf(item.getDetail()));
            }
            hits.add(new RiskHit(
                    String.join(LABEL_PATH_SEPARATOR, labels),
                    labels,
                    null,
                    null,
                    HitSource.MACHINE,
                    null, null, raw));
        }
    }

    private AuditResult.Builder baseBuilder(String requestId, Map<String, Object> context) {
        Map<String, Object> safe = context == null ? Map.of() : context;
        Map<String, Object> raw = new LinkedHashMap<>();
        // raw 会经不可变拷贝（Map.copyOf 语义），null 值一律不放
        if (requestId != null) {
            raw.put("RequestId", requestId);
        }
        String service = (String) safe.get(CONTEXT_SERVICE);
        if (service != null) {
            raw.put("Service", service);
        }
        return AuditResult.builder()
                .ok(true)
                .finalResult(true)
                .source(AuditSource.SYNC)
                .provider(provider())
                .product(VolcengineProviderConstants.PRODUCT)
                .service(service)
                .dataId((String) safe.get(CONTEXT_DATA_ID))
                .cost((Duration) safe.get(CONTEXT_COST))
                .requestId(requestId)
                .raw(raw);
    }

    /**
     * 响应元数据错误（网关/鉴权层）统一抛调用失败
     */
    private void assertNoMetadataError(ResponseMetadata metadata, Map<String, Object> context) {
        if (metadata == null || metadata.getError() == null) {
            return;
        }
        String requestId = metadata.getRequestId();
        throw VolcengineErrorMapper.fromResponseError(metadata.getError(), requestId);
    }

    /**
     * decision 三档映射：pass → PASS，suspect → REVIEW，reject → REJECT；
     * 缺失/未知值保守回退 REVIEW 并告警（枚举以官方文档为准）
     */
    static RiskSuggestion fromDecision(String decision) {
        if (decision == null || decision.isBlank()) {
            log.warn("volcengine decision missing, fallback to REVIEW");
            return RiskSuggestion.REVIEW;
        }
        RiskSuggestion mapped = switch (decision.toLowerCase(Locale.ROOT)) {
            case "pass" -> RiskSuggestion.PASS;
            case "suspect" -> RiskSuggestion.REVIEW;
            case "reject" -> RiskSuggestion.REJECT;
            default -> null;
        };
        if (mapped == null) {
            log.warn("volcengine unknown decision '{}' (decision enum is doc-defined), fallback to REVIEW",
                    decision);
            return RiskSuggestion.REVIEW;
        }
        return mapped;
    }

    private static List<String> labelsOf(String label, String subLabel) {
        List<String> labels = new ArrayList<>();
        if (label != null && !label.isBlank()) {
            labels.add(label);
        }
        if (subLabel != null && !subLabel.isBlank()) {
            labels.add(subLabel);
        }
        return labels;
    }

    private static String nullSafe(String value) {
        return value == null ? "" : value;
    }
}
