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
 * <p>映射规则：
 * <ul>
 *   <li>{@code riskLevel}：PASS→PASS、REVIEW→REVIEW、REJECT→REJECT（大小写不敏感，未知/缺失按 REVIEW 兜底）</li>
 *   <li>{@code finalResult}：1→终态、0→非终态（数美人审回传前的机审中间态）</li>
 *   <li>{@code resultType}：0→HitSource.MACHINE、1→HitSource.MANUAL</li>
 *   <li>{@code riskDetail.riskSource}：1000 无风险/1001 文字风险/1002 视觉图片风险 → {@code hit.raw.riskSource}</li>
 *   <li>{@code riskDetail.matchedLists[].words[]}：命中关键词进 {@code hit.raw.matchedWords}；
 *       {@code riskSegments}（用户内容原文片段）与 {@code auxInfo}（filteredText/contactResult）整体丢弃</li>
 *   <li>视频查询/回调：{@code auxInfo.frameDetail}（截帧）/ {@code audioDetail}（语音段）逐条 → hits
 *       （{@code hit.raw.kind}=frame/audio）；{@code imgUrl}/{@code audioUrl}（签名 URL）与
 *       {@code audioText}（语音转写原文）不透传</li>
 * </ul>
 *
 * @author L.David
 */
public class ShumeiAuditResultNormalizer implements AuditResultNormalizer {

    public static final String CONTEXT_EVENT_ID = "eventId";
    public static final String CONTEXT_DATA_ID = "dataId";
    public static final String CONTEXT_COST = "cost";

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
        return build(rawResponse, AuditSource.SYNC, context);
    }

    /**
     * 异步主动查询结果归一（/v4/image/query contents 单项，与同步响应同构）
     */
    public AuditResult normalizeQuery(Object rawResponse, Map<String, Object> context) {
        return build(rawResponse, AuditSource.ASYNC_QUERY, context);
    }

    /**
     * 异步回调报文归一（与同步响应同构）
     */
    public AuditResult normalizeCallback(Object rawResponse, Map<String, Object> context) {
        return build(rawResponse, AuditSource.ASYNC_CALLBACK, context);
    }

    /**
     * 视频查询结果归一（/video/query/v4 终态响应，code=1100；1101 中间态由操作层分流）
     */
    public AuditResult normalizeVideoQuery(ShumeiVideoQueryResponse response, Map<String, Object> context) {
        return videoBuild(response, AuditSource.ASYNC_QUERY, context);
    }

    /**
     * 视频回调报文归一（与查询响应同构）
     */
    public AuditResult normalizeVideoCallback(ShumeiVideoQueryResponse response, Map<String, Object> context) {
        return videoBuild(response, AuditSource.ASYNC_CALLBACK, context);
    }

    private AuditResult videoBuild(ShumeiVideoQueryResponse response, AuditSource source,
                                   Map<String, Object> context) {
        List<RiskHit> hits = videoHitsOf(response.getAuxInfo());
        // riskLevel 缺失时按命中兜底：无命中视为 PASS，有命中保守 REVIEW
        RiskSuggestion suggestion = response.getRiskLevel() == null || response.getRiskLevel().isBlank()
                ? (hits.isEmpty() ? RiskSuggestion.PASS : RiskSuggestion.REVIEW)
                : suggestionOf(response.getRiskLevel());
        Map<String, Object> raw = new LinkedHashMap<>();
        if (response.getCode() != null) {
            raw.put("code", response.getCode());
        }
        return AuditResult.builder()
                .ok(true)
                .suggestion(suggestion)
                .finalResult(true)
                .source(source)
                .provider(provider())
                .product(ShumeiProviderConstants.PRODUCT)
                .service((String) context.get(CONTEXT_EVENT_ID))
                .dataId((String) context.get(CONTEXT_DATA_ID))
                .cost((Duration) context.get(CONTEXT_COST))
                .requestId(response.getRequestId())
                .hits(hits)
                .raw(raw)
                .build();
    }

    /**
     * 视频命中：auxInfo.frameDetail（截帧）/audioDetail（语音段）逐条映射；
     * 帧粒度标签取 allLabels（含 probability），缺失时回退帧级 riskLabel1/2/3；
     * imgUrl/audioUrl（签名 URL）与 audioText（语音转写原文）不透传
     */
    private static List<RiskHit> videoHitsOf(ShumeiVideoQueryResponse.AuxInfo auxInfo) {
        if (auxInfo == null) {
            return List.of();
        }
        List<RiskHit> hits = new ArrayList<>();
        if (auxInfo.getFrameDetail() != null) {
            for (ShumeiVideoQueryResponse.FrameDetail detail : auxInfo.getFrameDetail()) {
                appendVideoDetailHits(hits, detail.getRiskLevel(), detail.getRiskLabel1(),
                        detail.getRiskLabel2(), detail.getRiskLabel3(), detail.getAllLabels(),
                        detail.getRiskDetail(), "frame");
            }
        }
        if (auxInfo.getAudioDetail() != null) {
            for (ShumeiVideoQueryResponse.AudioDetail detail : auxInfo.getAudioDetail()) {
                appendVideoDetailHits(hits, detail.getRiskLevel(), detail.getRiskLabel1(),
                        detail.getRiskLabel2(), detail.getRiskLabel3(), detail.getAllLabels(),
                        detail.getRiskDetail(), "audio");
            }
        }
        return hits;
    }

    private static void appendVideoDetailHits(List<RiskHit> hits, String riskLevel, String label1,
                                              String label2, String label3,
                                              List<ShumeiSyncResponse.LabelEntry> allLabels,
                                              JsonObject riskDetail, String kind) {
        if (riskLevel == null || "PASS".equalsIgnoreCase(riskLevel.trim())) {
            return;
        }
        if (allLabels != null && !allLabels.isEmpty()) {
            for (ShumeiSyncResponse.LabelEntry entry : allLabels) {
                if ("PASS".equalsIgnoreCase(entry.getRiskLevel())) {
                    continue;
                }
                hits.add(videoHitOf(entry.getRiskLevel(),
                        entry.getRiskLabel1(), entry.getRiskLabel2(), entry.getRiskLabel3(),
                        entry.getProbability(),
                        entry.getRiskDetail() != null ? entry.getRiskDetail() : riskDetail, kind));
            }
            return;
        }
        if (label1 == null || label1.isBlank()) {
            return;
        }
        hits.add(videoHitOf(riskLevel, label1, label2, label3, null, riskDetail, kind));
    }

    private static RiskHit videoHitOf(String riskLevel, String label1, String label2, String label3,
                                      Double probability, JsonObject riskDetail, String kind) {
        Map<String, Object> raw = hitRawOf(riskLevel, riskDetail);
        raw.put("kind", kind);
        return new RiskHit(
                labelPathOf(label1, label2, label3),
                labelsOf(label1, label2, label3),
                probability,
                probability == null ? null : ConfidenceScale.ZERO_TO_ONE,
                HitSource.MACHINE,
                null,
                null,
                raw);
    }

    private AuditResult build(Object rawResponse, AuditSource source, Map<String, Object> context) {
        if (!(rawResponse instanceof ShumeiSyncResponse response)) {
            throw new ProviderCallException("shumei normalizer received unexpected raw type: "
                    + (rawResponse == null ? "null" : rawResponse.getClass().getName()));
        }
        return AuditResult.builder()
                .ok(true)
                .suggestion(suggestionOf(response.getRiskLevel()))
                .finalResult(finalResultOf(response.getFinalResult()))
                .source(source)
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
