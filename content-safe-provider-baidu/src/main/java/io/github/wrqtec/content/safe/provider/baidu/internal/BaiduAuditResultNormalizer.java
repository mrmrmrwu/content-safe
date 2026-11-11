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

    /**
     * 归一化长视频拉取结果（data 须已产出结论且非审核失败，由操作前置判断）
     *
     * @param data 拉取数据体
     * @param logId 本次拉取日志 ID（可空）
     * @param context 归一化上下文
     */
    public AuditResult normalizeVideoPull(BaiduVideoPullResponse.Data data, Long logId,
                                          Map<String, Object> context) {
        return videoResultOf(data.getConclusionType(), data.getFrames(), data.getAudios(),
                logId == null ? null : String.valueOf(logId), AuditSource.ASYNC_QUERY, context);
    }

    /**
     * 归一化长视频回调结果（result 须终态且非审核失败，由操作前置判断）
     *
     * @param result 回调结果
     * @param context 归一化上下文（dataId 取提交时 extId）
     */
    public AuditResult normalizeVideoCallback(BaiduVideoCallbackPayload.Result result,
                                              Map<String, Object> context) {
        return videoResultOf(result.getConclusionType(), result.getFrames(), result.getAudios(),
                null, AuditSource.ASYNC_CALLBACK, context);
    }

    /**
     * 长视频结果归一：截帧与语音逐明细展开为 hit，原始报文不入 raw
     * （签名 URL 与转写原文不透传；命中词进 evidence，由 core EvidenceRedactor 统一脱敏）
     */
    private AuditResult videoResultOf(Integer conclusionType, List<BaiduVideoPullResponse.Frame> frames,
                                      List<BaiduVideoPullResponse.Audio> audios, String requestId,
                                      AuditSource source, Map<String, Object> context) {
        return AuditResult.builder()
                .ok(true)
                .suggestion(suggestionOf(conclusionType))
                .finalResult(true)
                .source(source)
                .provider(provider())
                .product(BaiduProviderConstants.PRODUCT)
                .service((String) context.get(CONTEXT_SERVICE))
                .dataId((String) context.get(CONTEXT_DATA_ID))
                .cost((Duration) context.get(CONTEXT_COST))
                .requestId(requestId)
                .hits(videoHitsOf(frames, audios))
                .build();
    }

    private static List<RiskHit> videoHitsOf(List<BaiduVideoPullResponse.Frame> frames,
                                             List<BaiduVideoPullResponse.Audio> audios) {
        List<RiskHit> hits = new ArrayList<>();
        if (frames != null) {
            for (BaiduVideoPullResponse.Frame frame : frames) {
                appendFrameHits(frame, hits);
            }
        }
        if (audios != null) {
            for (BaiduVideoPullResponse.Audio audio : audios) {
                appendAudioHits(audio, hits);
            }
        }
        return hits;
    }

    /**
     * 逐截帧命中：合规帧（conclusionType=1）跳过；frameUrl/frameThumbnailUrl 不透传
     */
    private static void appendFrameHits(BaiduVideoPullResponse.Frame frame, List<RiskHit> hits) {
        if (frame == null || frame.getData() == null
                || frame.getConclusionType() == null || frame.getConclusionType() == CONCLUSION_PASS) {
            return;
        }
        for (BaiduVideoPullResponse.Detail detail : frame.getData()) {
            if (detail == null) {
                continue;
            }
            Map<String, Object> raw = new LinkedHashMap<>();
            if (frame.getFrameTimeStamp() != null) {
                raw.put("frameTimeStamp", frame.getFrameTimeStamp());
            }
            if (detail.getDatasetName() != null) {
                raw.put("datasetName", detail.getDatasetName());
            }
            hits.add(new RiskHit(
                    labelPathOf(detail.getType(), detail.getSubType()),
                    detail.getMsg() == null || detail.getMsg().isBlank()
                            ? List.of() : List.of(detail.getMsg()),
                    detail.getProbability(),
                    detail.getProbability() == null ? null : ConfidenceScale.ZERO_TO_ONE,
                    HitSource.MACHINE,
                    wordsOfFrameHits(detail.getHits()),
                    null,
                    raw));
        }
    }

    /**
     * 逐语音分段命中：text（转写原文）与 audioUrl（签名地址）不透传
     */
    private static void appendAudioHits(BaiduVideoPullResponse.Audio audio, List<RiskHit> hits) {
        if (audio == null || audio.getAudioAuditResult() == null
                || audio.getAudioAuditResult().getData() == null) {
            return;
        }
        for (BaiduVideoPullResponse.AudioData segment : audio.getAudioAuditResult().getData()) {
            if (segment == null || segment.getAuditData() == null) {
                continue;
            }
            for (BaiduVideoPullResponse.AudioDetail detail : segment.getAuditData()) {
                if (detail == null) {
                    continue;
                }
                Map<String, Object> raw = new LinkedHashMap<>();
                if (audio.getStartTime() != null) {
                    raw.put("startTime", audio.getStartTime());
                }
                if (audio.getEndTime() != null) {
                    raw.put("endTime", audio.getEndTime());
                }
                Double confidence = confidenceOf(detail.getHits());
                hits.add(new RiskHit(
                        labelPathOf(detail.getType(), detail.getSubType()),
                        detail.getMsg() == null || detail.getMsg().isBlank()
                                ? List.of() : List.of(detail.getMsg()),
                        confidence,
                        confidence == null ? null : ConfidenceScale.ZERO_TO_ONE,
                        HitSource.MACHINE,
                        wordsOfAudioHits(detail.getHits()),
                        null,
                        raw));
            }
        }
    }

    private static String labelPathOf(Integer type, Integer subType) {
        String base = type == null ? "unknown" : String.valueOf(type);
        return subType == null ? base : base + "." + subType;
    }

    private static Double confidenceOf(List<BaiduVideoPullResponse.AudioHit> hits) {
        if (hits == null) {
            return null;
        }
        for (BaiduVideoPullResponse.AudioHit hit : hits) {
            if (hit != null && hit.getProbability() != null) {
                return hit.getProbability();
            }
        }
        return null;
    }

    /**
     * 截帧命中词汇总（逗号拼接；由 core EvidenceRedactor 统一脱敏）
     */
    private static String wordsOfFrameHits(List<BaiduVideoPullResponse.Hit> hits) {
        if (hits == null || hits.isEmpty()) {
            return null;
        }
        List<String> words = new ArrayList<>();
        for (BaiduVideoPullResponse.Hit hit : hits) {
            if (hit != null && hit.getWords() != null) {
                words.addAll(hit.getWords());
            }
        }
        return words.isEmpty() ? null : String.join(",", words);
    }

    /**
     * 语音命中词汇总（逗号拼接；由 core EvidenceRedactor 统一脱敏）
     */
    private static String wordsOfAudioHits(List<BaiduVideoPullResponse.AudioHit> hits) {
        if (hits == null || hits.isEmpty()) {
            return null;
        }
        List<String> words = new ArrayList<>();
        for (BaiduVideoPullResponse.AudioHit hit : hits) {
            if (hit != null && hit.getWords() != null) {
                words.addAll(hit.getWords());
            }
        }
        return words.isEmpty() ? null : String.join(",", words);
    }
}
