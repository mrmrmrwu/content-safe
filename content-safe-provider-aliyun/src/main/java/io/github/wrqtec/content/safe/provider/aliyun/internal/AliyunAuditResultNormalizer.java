package io.github.wrqtec.content.safe.provider.aliyun.internal;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.aliyun.green20220302.models.DescribeImageModerationResultResponseBody;
import com.aliyun.green20220302.models.ImageModerationResponseBody;
import com.aliyun.green20220302.models.TextModerationResponseBody;
import io.github.wrqtec.content.safe.api.AuditResult;
import io.github.wrqtec.content.safe.api.RiskHit;
import io.github.wrqtec.content.safe.api.enums.AuditSource;
import io.github.wrqtec.content.safe.api.enums.ConfidenceScale;
import io.github.wrqtec.content.safe.api.enums.HitSource;
import io.github.wrqtec.content.safe.api.enums.RiskSuggestion;
import io.github.wrqtec.content.safe.api.exception.ProviderCallException;
import io.github.wrqtec.content.safe.model.ProviderPlan;
import io.github.wrqtec.content.safe.provider.aliyun.AliyunProviderConstants;
import io.github.wrqtec.content.safe.spi.AuditResultNormalizer;

/**
 * 阿里云内容安全 2.0 响应归一化（铁律 §5.4：provider 返回结构只允许由 Normalizer 翻译）
 *
 * <p>suggestion 映射（用户决策 2026-11-06）：riskLevel 三档 ——
 * none/low → PASS，medium → REVIEW，high → REJECT；
 * 文本的 riskLevel 只在 reason JSON 字符串内（SDK 模型无该字段），
 * 缺失时回退 labels 判空（空 → PASS，非空 → REJECT）
 *
 * <p>安全约束：raw 只保留 {code, requestId}，绝不放用户内容原文（铁律 §5.6）
 *
 * @author L.David
 */
public class AliyunAuditResultNormalizer implements AuditResultNormalizer {

    /** 归一化上下文键 */
    public static final String CONTEXT_SERVICE = "service";
    public static final String CONTEXT_DATA_ID = "dataId";
    public static final String CONTEXT_COST = "cost";

    /** 图片通过标签：全部命中为该值时视为通过 */
    private static final String PASS_LABEL = "nonLabel";

    /** reason JSON 字符串中的 riskLevel 提取（best-effort，解析失败不抛错） */
    private static final Pattern RISK_LEVEL_PATTERN =
            Pattern.compile("\"riskLevel\"\\s*:\\s*\"([A-Za-z]+)\"");

    /** reason JSON 字符串中的置信分提取（confidence / score / rate） */
    private static final Pattern CONFIDENCE_PATTERN =
            Pattern.compile("\"(?:confidence|score|rate)\"\\s*:\\s*\"?(\\d*\\.?\\d+)\"?");

    /** 标签分隔符（阿里云 labels 为逗号分隔字符串） */
    private static final String LABEL_SEPARATOR = ",";

    /** 标签路径连接符 */
    private static final String LABEL_PATH_SEPARATOR = "/";

    @Override
    public String provider() {
        return AliyunProviderConstants.PROVIDER;
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
        if (rawResponse instanceof TextModerationResponseBody body) {
            return normalizeText(body, context);
        }
        if (rawResponse instanceof ImageModerationResponseBody body) {
            return normalizeImage(body, context);
        }
        if (rawResponse instanceof DescribeImageModerationResultResponseBody body) {
            return normalizeImageAsyncResult(body, context);
        }
        throw new ProviderCallException("unsupported aliyun raw response type: "
                + (rawResponse == null ? "null" : rawResponse.getClass().getName()));
    }

    private AuditResult normalizeText(TextModerationResponseBody body, Map<String, Object> context) {
        AuditResult.Builder builder = baseBuilder(body.getCode(), body.getRequestId(), context);
        TextModerationResponseBody.TextModerationResponseBodyData data = body.getData();
        if (data == null) {
            return builder.suggestion(RiskSuggestion.PASS).build();
        }
        String reason = data.getReason();
        RiskSuggestion byRiskLevel = fromRiskLevel(extractGroup(RISK_LEVEL_PATTERN, reason));
        List<String> labels = splitLabels(data.getLabels());
        RiskSuggestion suggestion = byRiskLevel != null ? byRiskLevel
                : labels.isEmpty() ? RiskSuggestion.PASS : RiskSuggestion.REJECT;
        builder.suggestion(suggestion);
        if (RiskSuggestion.PASS != suggestion && !labels.isEmpty()) {
            Double confidence = extractConfidence(reason);
            RiskHit hit = new RiskHit(
                    String.join(LABEL_PATH_SEPARATOR, labels),
                    labels,
                    confidence,
                    scaleOf(confidence),
                    HitSource.MACHINE,
                    null, null, Map.of());
            builder.hits(List.of(hit));
        }
        return builder.build();
    }

    private AuditResult normalizeImage(ImageModerationResponseBody body, Map<String, Object> context) {
        AuditResult.Builder builder = baseBuilder(body.getCode(), body.getRequestId(), context);
        ImageModerationResponseBody.ImageModerationResponseBodyData data = body.getData();
        if (data == null || data.getResult() == null || data.getResult().isEmpty()) {
            return builder.suggestion(RiskSuggestion.PASS).build();
        }
        List<RiskHit> hits = new ArrayList<>();
        for (ImageModerationResponseBody.ImageModerationResponseBodyDataResult item : data.getResult()) {
            if (item == null || PASS_LABEL.equals(item.getLabel())) {
                continue;
            }
            Map<String, Object> hitRaw = item.getDescription() == null || item.getDescription().isBlank()
                    ? Map.of() : Map.of("description", item.getDescription());
            hits.add(new RiskHit(
                    item.getLabel(),
                    List.of(item.getLabel()),
                    item.getConfidence() == null ? null : item.getConfidence().doubleValue(),
                    ConfidenceScale.PERCENTAGE,
                    HitSource.MACHINE,
                    null, null, hitRaw));
        }
        RiskSuggestion byRiskLevel = fromRiskLevel(data.getRiskLevel());
        RiskSuggestion suggestion = byRiskLevel != null ? byRiskLevel
                : hits.isEmpty() ? RiskSuggestion.PASS : RiskSuggestion.REJECT;
        return builder.suggestion(suggestion).hits(hits).build();
    }

    /**
     * 图片异步检测结果归一化（DescribeImageModerationResult）。data.result 与同步
     * ImageModeration 同构（label/confidence/description），复用同一套标签解析；
     * code=280（检测中）不走本方法，由操作层直接产出中间态结果
     *
     * @param body    查询响应体
     * @param context 归一化上下文（service / dataId / cost）
     * @return 终态审核结果
     */
    AuditResult normalizeImageAsyncResult(DescribeImageModerationResultResponseBody body,
                                          Map<String, Object> context) {
        AuditResult.Builder builder = baseBuilder(body.getCode(), body.getRequestId(), context);
        DescribeImageModerationResultResponseBody.DescribeImageModerationResultResponseBodyData data = body.getData();
        if (data == null || data.getResult() == null || data.getResult().isEmpty()) {
            return builder.suggestion(RiskSuggestion.PASS).build();
        }
        List<RiskHit> hits = new ArrayList<>();
        for (DescribeImageModerationResultResponseBody.DescribeImageModerationResultResponseBodyDataResult item
                : data.getResult()) {
            if (item == null || PASS_LABEL.equals(item.getLabel())) {
                continue;
            }
            Map<String, Object> hitRaw = item.getDescription() == null || item.getDescription().isBlank()
                    ? Map.of() : Map.of("description", item.getDescription());
            hits.add(new RiskHit(
                    item.getLabel(),
                    List.of(item.getLabel()),
                    item.getConfidence() == null ? null : item.getConfidence().doubleValue(),
                    ConfidenceScale.PERCENTAGE,
                    HitSource.MACHINE,
                    null, null, hitRaw));
        }
        RiskSuggestion byRiskLevel = fromRiskLevel(data.getRiskLevel());
        RiskSuggestion suggestion = byRiskLevel != null ? byRiskLevel
                : hits.isEmpty() ? RiskSuggestion.PASS : RiskSuggestion.REJECT;
        return builder.suggestion(suggestion).hits(hits).build();
    }

    private AuditResult.Builder baseBuilder(Integer code, String requestId, Map<String, Object> context) {
        Map<String, Object> safe = context == null ? Map.of() : context;
        Map<String, Object> raw = new LinkedHashMap<>();
        if (code != null) {
            raw.put("code", code);
        }
        if (requestId != null) {
            raw.put("requestId", requestId);
        }
        return AuditResult.builder()
                .ok(true)
                .finalResult(true)
                .source(AuditSource.SYNC)
                .provider(provider())
                .product(AliyunProviderConstants.PRODUCT)
                .service((String) safe.get(CONTEXT_SERVICE))
                .dataId((String) safe.get(CONTEXT_DATA_ID))
                .cost((Duration) safe.get(CONTEXT_COST))
                .requestId(requestId)
                .raw(raw);
    }

    /**
     * riskLevel 三档映射：none/low → PASS，medium → REVIEW，high → REJECT；未知值返回 null（回退规则兜底）
     */
    static RiskSuggestion fromRiskLevel(String riskLevel) {
        if (riskLevel == null) {
            return null;
        }
        return switch (riskLevel.toLowerCase(java.util.Locale.ROOT)) {
            case "none", "low" -> RiskSuggestion.PASS;
            case "medium" -> RiskSuggestion.REVIEW;
            case "high" -> RiskSuggestion.REJECT;
            default -> null;
        };
    }

    private static List<String> splitLabels(String labels) {
        if (labels == null || labels.isBlank()) {
            return List.of();
        }
        return Arrays.stream(labels.split(LABEL_SEPARATOR))
                .map(String::trim)
                .filter(label -> !label.isEmpty())
                .toList();
    }

    private static String extractGroup(Pattern pattern, String text) {
        if (text == null) {
            return null;
        }
        Matcher matcher = pattern.matcher(text);
        return matcher.find() ? matcher.group(1) : null;
    }

    /**
     * 置信度 best-effort 提取：&gt;1 记 PERCENTAGE，否则 ZERO_TO_ONE；解析失败返回 null（不抛错）
     */
    static Double extractConfidence(String reason) {
        String value = extractGroup(CONFIDENCE_PATTERN, reason);
        if (value == null) {
            return null;
        }
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * 置信度分制推导（&gt;1 按百分制）
     */
    static ConfidenceScale scaleOf(Double confidence) {
        return confidence != null && confidence > 1D ? ConfidenceScale.PERCENTAGE : ConfidenceScale.ZERO_TO_ONE;
    }
}
