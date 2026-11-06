package io.github.wrqtec.content.safe.provider.baidu.internal;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import io.github.wrqtec.content.safe.api.AuditResult;
import io.github.wrqtec.content.safe.api.RiskHit;
import io.github.wrqtec.content.safe.api.enums.AuditSource;
import io.github.wrqtec.content.safe.api.enums.ConfidenceScale;
import io.github.wrqtec.content.safe.api.enums.HitSource;
import io.github.wrqtec.content.safe.api.enums.RiskSuggestion;
import io.github.wrqtec.content.safe.api.exception.ProviderCallException;
import io.github.wrqtec.content.safe.model.ProviderPlan;
import io.github.wrqtec.content.safe.provider.baidu.BaiduProviderConstants;
import io.github.wrqtec.content.safe.spi.AuditResultNormalizer;

/**
 * 百度响应 → 统一 {@link AuditResult}
 *
 * <p>conclusionType 映射：1=合规→PASS、2=不合规→REJECT、3=疑似→REVIEW、4=审核失败→ERROR
 * （审核失败是业务结论而非调用失败，ok 保持 true）。
 *
 * @author L.David
 */
public class BaiduAuditResultNormalizer implements AuditResultNormalizer {

    public static final String CONTEXT_SERVICE = "service";
    public static final String CONTEXT_DATA_ID = "dataId";
    public static final String CONTEXT_COST = "cost";

    /** 结论类型：合规 */
    private static final int CONCLUSION_PASS = 1;

    /** 结论类型：不合规 */
    private static final int CONCLUSION_REJECT = 2;

    /** 结论类型：疑似 */
    private static final int CONCLUSION_REVIEW = 3;

    /** 结论类型：审核失败 */
    private static final int CONCLUSION_ERROR = 4;

    @Override
    public String provider() {
        return BaiduProviderConstants.PROVIDER;
    }

    /**
     * 归一化上下文
     *
     * @param plan 操作计划（service 透传）
     * @param dataId 业务数据 ID（可空）
     * @param cost 调用耗时（可空）
     */
    public static Map<String, Object> context(ProviderPlan plan, String dataId, Duration cost) {
        Map<String, Object> context = new LinkedHashMap<>();
        context.put(CONTEXT_SERVICE, plan.service());
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
        if (!(rawResponse instanceof BaiduCensorResponse response)) {
            throw new ProviderCallException("baidu normalizer received unexpected raw type: "
                    + (rawResponse == null ? "null" : rawResponse.getClass().getName()));
        }
        RiskSuggestion suggestion = suggestionOf(response.getConclusionType());
        List<RiskHit> hits = hitsOf(response);
        return AuditResult.builder()
                .ok(true)
                .suggestion(suggestion)
                .finalResult(true)
                .source(AuditSource.SYNC)
                .provider(provider())
                .product(BaiduProviderConstants.PRODUCT)
                .service((String) context.get(CONTEXT_SERVICE))
                .dataId((String) context.get(CONTEXT_DATA_ID))
                .cost((Duration) context.get(CONTEXT_COST))
                .requestId(response.getLogId() == null ? null : String.valueOf(response.getLogId()))
                .hits(hits)
                .raw(rawOf(response))
                .build();
    }

    /**
     * conclusionType → 统一三档建议；4=审核失败映射 ERROR；未知值按不合规兜底
     */
    static RiskSuggestion suggestionOf(Integer conclusionType) {
        if (conclusionType == null) {
            return RiskSuggestion.REJECT;
        }
        return switch (conclusionType) {
            case CONCLUSION_PASS -> RiskSuggestion.PASS;
            case CONCLUSION_REJECT -> RiskSuggestion.REJECT;
            case CONCLUSION_REVIEW -> RiskSuggestion.REVIEW;
            case CONCLUSION_ERROR -> RiskSuggestion.ERROR;
            default -> RiskSuggestion.REJECT;
        };
    }

    private static List<RiskHit> hitsOf(BaiduCensorResponse response) {
        List<BaiduCensorResponse.Data> dataList = response.getData();
        if (dataList == null || dataList.isEmpty()) {
            return List.of();
        }
        List<RiskHit> hits = new ArrayList<>(dataList.size());
        for (BaiduCensorResponse.Data data : dataList) {
            hits.add(new RiskHit(
                    labelPathOf(data),
                    data.getMsg() == null || data.getMsg().isBlank() ? List.of() : List.of(data.getMsg()),
                    data.getProbability(),
                    data.getProbability() == null ? null : ConfidenceScale.ZERO_TO_ONE,
                    HitSource.MACHINE,
                    wordsOf(data),
                    null,
                    null));
        }
        return hits;
    }

    private static String labelPathOf(BaiduCensorResponse.Data data) {
        String type = data.getType() == null ? "unknown" : String.valueOf(data.getType());
        String subType = data.getSubType() == null ? "" : "." + data.getSubType();
        return type + subType;
    }

    /**
     * 命中词汇总（逗号拼接；由 core EvidenceRedactor 统一脱敏）
     */
    private static String wordsOf(BaiduCensorResponse.Data data) {
        if (data.getHits() == null || data.getHits().isEmpty()) {
            return null;
        }
        List<String> words = new ArrayList<>();
        for (BaiduCensorResponse.Hit hit : data.getHits()) {
            if (hit.getWords() != null) {
                words.addAll(hit.getWords());
            }
        }
        return words.isEmpty() ? null : String.join(",", words);
    }

    private static Map<String, Object> rawOf(BaiduCensorResponse response) {
        if (response.getLogId() == null) {
            return null;
        }
        return Map.of("log_id", response.getLogId());
    }
}
