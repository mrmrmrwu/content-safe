package io.github.wrqtec.content.safe.provider.shumei.internal;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.github.wrqtec.content.safe.api.AuditResult;
import io.github.wrqtec.content.safe.api.RiskHit;
import io.github.wrqtec.content.safe.api.enums.AuditSource;
import io.github.wrqtec.content.safe.api.enums.ConfidenceScale;
import io.github.wrqtec.content.safe.api.enums.HitSource;
import io.github.wrqtec.content.safe.api.enums.RiskSuggestion;
import io.github.wrqtec.content.safe.api.exception.ProviderCallException;
import io.github.wrqtec.content.safe.model.ProviderPlan;
import io.github.wrqtec.content.safe.provider.shumei.ShumeiProviderConstants;
import io.github.wrqtec.content.safe.spi.AuditResultNormalizer;

/**
 * 数美响应 → 统一 {@link AuditResult}
 *
 * <p>映射规则（T-017 验收）：
 * <ul>
 *   <li>{@code riskLevel}：PASS→PASS、REVIEW→REVIEW、REJECT→REJECT（大小写不敏感，未知/缺失按 REVIEW 兜底）</li>
 *   <li>{@code finalResult}：1→终态、0→非终态（数美人审回传前的机审中间态）</li>
 *   <li>{@code resultType}：0→HitSource.MACHINE、1→HitSource.MANUAL</li>
 *   <li>{@code riskDetail.riskSource}：1000 无风险/1001 文字风险/1002 视觉图片风险 → {@code hit.raw.riskSource}</li>
 *   <li>{@code riskDetail.matchedLists[].words[]}：命中关键词进 {@code hit.raw.matchedWords}；
 *       {@code riskSegments}（用户内容原文片段）与 {@code auxInfo}（filteredText/contactResult）整体丢弃</li>
 * </ul>
 *
 * @author L.David
 */
public class ShumeiAuditResultNormalizer implements AuditResultNormalizer {

    public static final String CONTEXT_EVENT_ID = "eventId";
    public static final String CONTEXT_DATA_ID = "dataId";
    public static final String CONTEXT_COST = "cost";

    /** riskSource：无风险 */
    public static final int RISK_SOURCE_NONE = 1000;

    /** riskSource：文字风险 */
    public static final int RISK_SOURCE_TEXT = 1001;

    /** riskSource：视觉图片风险 */
    public static final int RISK_SOURCE_IMAGE = 1002;

    /** 最终结果取值：1=最终 */
    private static final int FINAL_RESULT_FINAL = 1;

    /** 结果类型：机审 */
    private static final int RESULT_TYPE_MACHINE = 0;

    /** 结果类型：人审 */
    private static final int RESULT_TYPE_MANUAL = 1;

    @Override
    public String provider() {
        return ShumeiProviderConstants.PROVIDER;
    }

    /**
     * 归一化上下文
     *
     * @param plan 操作计划（eventId 透传）
     * @param dataId 业务数据 ID（可空）
     * @param cost 调用耗时（可空）
     */
    public static Map<String, Object> context(ProviderPlan plan, String dataId, Duration cost) {
        Map<String, Object> context = new LinkedHashMap<>();
        context.put(CONTEXT_EVENT_ID, plan.service());
        if (dataId != null) {
            context.put(CONTEXT_DATA_ID, dataId);
        }
        if (cost != null) {
            context.put(CONTEXT_COST, cost);
        }
        return context;
    }

    @Override
    public AuditResult normalize(Object rawResponse, Map<String, Object> context) {
        if (!(rawResponse instanceof ShumeiSyncResponse response)) {
            throw new ProviderCallException("shumei normalizer received unexpected raw type: "
                    + (rawResponse == null ? "null" : rawResponse.getClass().getName()));
        }
        return AuditResult.builder()
                .ok(true)
                .suggestion(suggestionOf(response.getRiskLevel()))
                .finalResult(finalResultOf(response.getFinalResult()))
                .source(AuditSource.SYNC)
                .provider(provider())
                .product(ShumeiProviderConstants.PRODUCT)
                .service((String) context.get(CONTEXT_EVENT_ID))
                .dataId((String) context.get(CONTEXT_DATA_ID))
                .cost((Duration) context.get(CONTEXT_COST))
                .requestId(response.getRequestId())
                .hits(hitsOf(response))
                .raw(rawOf(response))
                .build();
    }

    /**
     * riskLevel → 统一三档建议；未知/缺失按 REVIEW 保守兜底
     */
    static RiskSuggestion suggestionOf(String riskLevel) {
        if (riskLevel == null || riskLevel.isBlank()) {
            return RiskSuggestion.REVIEW;
        }
        return switch (riskLevel.trim().toUpperCase(Locale.ROOT)) {
            case "PASS" -> RiskSuggestion.PASS;
            case "REJECT" -> RiskSuggestion.REJECT;
            case "REVIEW" -> RiskSuggestion.REVIEW;
            default -> RiskSuggestion.REVIEW;
        };
    }

    /**
     * finalResult（1=最终）→ 统一终态标记；缺失按终态处理（仅接机审时官方默认返回 1）
     */
    static boolean finalResultOf(Integer finalResult) {
        return finalResult == null || finalResult == FINAL_RESULT_FINAL;
    }

    /**
     * resultType（0=机审，1=人审）→ 命中来源；未知按机审
     */
    static HitSource hitSourceOf(Integer resultType) {
        if (resultType != null && resultType == RESULT_TYPE_MANUAL) {
            return HitSource.MANUAL;
        }
        return HitSource.MACHINE;
    }

    private static List<RiskHit> hitsOf(ShumeiSyncResponse response) {
        List<ShumeiSyncResponse.LabelEntry> allLabels = response.getAllLabels();
        HitSource source = hitSourceOf(response.getResultType());
        if (allLabels == null || allLabels.isEmpty()) {
            return rootHitOf(response, source);
        }
        List<RiskHit> hits = new ArrayList<>(allLabels.size());
        for (ShumeiSyncResponse.LabelEntry entry : allLabels) {
            if ("PASS".equalsIgnoreCase(entry.getRiskLevel())) {
                continue;
            }
            hits.add(new RiskHit(
                    labelPathOf(entry.getRiskLabel1(), entry.getRiskLabel2(), entry.getRiskLabel3()),
                    labelsOf(entry.getRiskLabel1(), entry.getRiskLabel2(), entry.getRiskLabel3()),
                    entry.getProbability(),
                    entry.getProbability() == null ? null : ConfidenceScale.ZERO_TO_ONE,
                    source,
                    null,
                    null,
                    hitRawOf(entry.getRiskLevel(),
                            entry.getRiskDetail() != null ? entry.getRiskDetail() : response.getRiskDetail())));
        }
        return hits;
    }

    /**
     * allLabels 缺失时的兜底命中：根级标签 + 根级 riskDetail
     */
    private static List<RiskHit> rootHitOf(ShumeiSyncResponse response, HitSource source) {
        if ("PASS".equalsIgnoreCase(response.getRiskLevel())) {
            return List.of();
        }
        if (response.getRiskLabel1() == null || response.getRiskLabel1().isBlank()) {
            return List.of();
        }
        return List.of(new RiskHit(
                labelPathOf(response.getRiskLabel1(), response.getRiskLabel2(), response.getRiskLabel3()),
                labelsOf(response.getRiskLabel1(), response.getRiskLabel2(), response.getRiskLabel3()),
                null,
                null,
                source,
                null,
                null,
                hitRawOf(response.getRiskLevel(), response.getRiskDetail())));
    }

    private static String labelPathOf(String label1, String label2, String label3) {
        StringBuilder path = new StringBuilder(label1 == null || label1.isBlank() ? "unknown" : label1.trim());
        if (label2 != null && !label2.isBlank()) {
            path.append('/').append(label2.trim());
        }
        if (label3 != null && !label3.isBlank()) {
            path.append('/').append(label3.trim());
        }
        return path.toString();
    }

    private static List<String> labelsOf(String label1, String label2, String label3) {
        List<String> labels = new ArrayList<>(3);
        for (String label : new String[] {label1, label2, label3}) {
            if (label != null && !label.isBlank()) {
                labels.add(label.trim());
            }
        }
        return labels;
    }

    /**
     * 单条命中 raw：riskLevel、riskSource、matchedWords（命中关键词，不进日志）；
     * riskDetail 其余内容（segments 片段、faces 人名等用户内容）不透传
     */
    private static Map<String, Object> hitRawOf(String riskLevel, JsonObject riskDetail) {
        Map<String, Object> raw = new LinkedHashMap<>();
        if (riskLevel != null) {
            raw.put("riskLevel", riskLevel);
        }
        if (riskDetail == null) {
            return raw;
        }
        JsonElement riskSource = riskDetail.get("riskSource");
        if (riskSource != null && riskSource.isJsonPrimitive()) {
            raw.put("riskSource", riskSource.getAsInt());
        }
        JsonArray matchedWords = matchedWordsOf(riskDetail);
        if (!matchedWords.isEmpty()) {
            raw.put("matchedWords", matchedWords);
        }
        return raw;
    }

    /**
     * riskDetail.matchedLists[].words[] → [{word, position}]（仅关键词与位置）
     */
    private static JsonArray matchedWordsOf(JsonObject riskDetail) {
        JsonArray matchedWords = new JsonArray();
        JsonElement matchedLists = riskDetail.get("matchedLists");
        if (matchedLists == null || !matchedLists.isJsonArray()) {
            return matchedWords;
        }
        for (JsonElement listElement : matchedLists.getAsJsonArray()) {
            if (!listElement.isJsonObject()) {
                continue;
            }
            JsonElement words = listElement.getAsJsonObject().get("words");
            if (words == null || !words.isJsonArray()) {
                continue;
            }
            for (JsonElement wordElement : words.getAsJsonArray()) {
                if (wordElement.isJsonObject()) {
                    matchedWords.add(wordElement.getAsJsonObject());
                }
            }
        }
        return matchedWords;
    }

    private static Map<String, Object> rawOf(ShumeiSyncResponse response) {
        Map<String, Object> raw = new LinkedHashMap<>();
        if (response.getCode() != null) {
            raw.put("code", response.getCode());
        }
        if (response.getFinalResult() != null) {
            raw.put("finalResult", response.getFinalResult());
        }
        if (response.getResultType() != null) {
            raw.put("resultType", response.getResultType());
        }
        return raw;
    }
}
