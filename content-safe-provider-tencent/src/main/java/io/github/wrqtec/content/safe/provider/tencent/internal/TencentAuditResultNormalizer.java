package io.github.wrqtec.content.safe.provider.tencent.internal;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.tencentcloudapi.ims.v20201229.models.ImageModerationResponse;
import com.tencentcloudapi.ims.v20201229.models.LabelResult;
import com.tencentcloudapi.ims.v20201229.models.ObjectDetail;
import com.tencentcloudapi.ims.v20201229.models.ObjectResult;
import com.tencentcloudapi.ims.v20201229.models.OcrResult;
import com.tencentcloudapi.tms.v20201229.models.TextModerationResponse;
import com.tencentcloudapi.vm.v20210922.models.AudioResult;
import com.tencentcloudapi.vm.v20210922.models.AudioSegments;
import com.tencentcloudapi.vm.v20210922.models.DescribeTaskDetailResponse;
import com.tencentcloudapi.vm.v20210922.models.ImageResult;
import com.tencentcloudapi.vm.v20210922.models.ImageSegments;
import com.tencentcloudapi.vm.v20210922.models.TaskLabel;
import io.github.wrqtec.content.safe.api.AuditResult;
import io.github.wrqtec.content.safe.api.RiskHit;
import io.github.wrqtec.content.safe.api.enums.AuditSource;
import io.github.wrqtec.content.safe.api.enums.ConfidenceScale;
import io.github.wrqtec.content.safe.api.enums.HitSource;
import io.github.wrqtec.content.safe.api.enums.RiskSuggestion;
import io.github.wrqtec.content.safe.api.exception.ProviderCallException;
import io.github.wrqtec.content.safe.model.ProviderPlan;
import io.github.wrqtec.content.safe.provider.tencent.TencentProviderConstants;
import io.github.wrqtec.content.safe.spi.AuditResultNormalizer;

/**
 * 腾讯云内容安全（TMS/IMS/VM）响应归一化（铁律 §5.4：provider 返回结构
 * 只允许由 Normalizer 翻译）
 *
 * <p>suggestion 映射：Pass → PASS，Review → REVIEW，Block → REJECT；
 * 未知值回退按 Label 判 Normal（Normal → PASS，其余 → REJECT）
 *
 * <p>安全约束（铁律 §5.6/§9）：raw 只保留 {RequestId, BizType}；
 * OCR 文本原文（OcrResults.Text）、TMS ContextText 属用户内容，
 * 绝不进入 evidence/raw/日志
 *
 * @author L.David
 */
public class TencentAuditResultNormalizer implements AuditResultNormalizer {

    /** 归一化上下文键 */
    public static final String CONTEXT_SERVICE = "service";
    public static final String CONTEXT_DATA_ID = "dataId";
    public static final String CONTEXT_COST = "cost";

    /** 通过标签 */
    private static final String PASS_LABEL = "Normal";

    /** 标签路径连接符 */
    private static final String LABEL_PATH_SEPARATOR = "/";

    @Override
    public String provider() {
        return TencentProviderConstants.PROVIDER;
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
        if (rawResponse instanceof TextModerationResponse response) {
            return normalizeText(response, context);
        }
        if (rawResponse instanceof ImageModerationResponse response) {
            return normalizeImage(response, AuditSource.SYNC, context);
        }
        throw new ProviderCallException("unsupported tencent raw response type: "
                + (rawResponse == null ? "null" : rawResponse.getClass().getName()));
    }

    /**
     * 异步回调报文归一化：回调体与同步 ImageModeration 输出同构
     * （官方无独立回调格式），仅 AuditSource 标记为 ASYNC_CALLBACK
     *
     * @param response 回调报文反序列化结果
     * @param context  归一化上下文
     * @return 审核结果
     */
    public AuditResult normalizeImageCallback(ImageModerationResponse response, Map<String, Object> context) {
        return normalizeImage(response, AuditSource.ASYNC_CALLBACK, context);
    }

    /**
     * 视频任务轮询终态归一化（Status=FINISH 的 DescribeTaskDetail 响应）。
     * 命中来源：任务级 Labels、音频分段（AudioSegments HitFlag=1）、
     * 截帧分段（ImageSegments HitFlag=1）；视频分段（VideoSegments）无
     * 独立标签，仅承载片段媒体地址，不参与 hits
     *
     * @param response 查询响应
     * @param context  归一化上下文
     * @return 审核结果（source=ASYNC_QUERY）
     */
    public AuditResult normalizeVideoQuery(DescribeTaskDetailResponse response, Map<String, Object> context) {
        return normalizeVideoTaskDetail(response, AuditSource.ASYNC_QUERY, context);
    }

    /**
     * 视频任务回调归一化（回调报文与 DescribeTaskDetail 响应同构）。
     * 语音转写文本（AudioResult.Text）、片段媒体地址（VideoUrl/AudioUrl）、
     * 命中片段（HitSnippetInfos）与截帧图地址（ImageResult.Url）不透传
     *
     * @param response 回调报文反序列化结果
     * @param context  归一化上下文
     * @return 审核结果（source=ASYNC_CALLBACK）
     */
    public AuditResult normalizeVideoCallback(DescribeTaskDetailResponse response, Map<String, Object> context) {
        return normalizeVideoTaskDetail(response, AuditSource.ASYNC_CALLBACK, context);
    }

    /**
     * 视频任务中间态结果：finalResult=false、suggestion=null，raw 仅含
     * status（不含任务内容）
     */
    public AuditResult inProgressVideo(DescribeTaskDetailResponse response, String status,
                                       Map<String, Object> context) {
        Map<String, Object> safe = context == null ? Map.of() : context;
        return AuditResult.builder()
                .ok(true)
                .suggestion(null)
                .finalResult(false)
                .source(AuditSource.ASYNC_QUERY)
                .provider(provider())
                .product(TencentProviderConstants.PRODUCT)
                .service((String) safe.get(CONTEXT_SERVICE))
                .dataId((String) safe.get(CONTEXT_DATA_ID))
                .cost((Duration) safe.get(CONTEXT_COST))
                .requestId(response.getRequestId())
                .raw(Map.of("status", nullSafe(status)))
                .build();
    }

    private AuditResult normalizeVideoTaskDetail(DescribeTaskDetailResponse response, AuditSource source,
                                                 Map<String, Object> context) {
        AuditResult.Builder builder = baseBuilder(response.getRequestId(), response.getBizType(), context)
                .source(source);
        RiskSuggestion suggestion = fromSuggestion(response.getSuggestion());
        if (suggestion == null) {
            suggestion = PASS_LABEL.equals(response.getLabel()) ? RiskSuggestion.PASS : RiskSuggestion.REJECT;
        }
        builder.suggestion(suggestion);
        if (RiskSuggestion.PASS == suggestion) {
            return builder.build();
        }
        List<RiskHit> hits = new ArrayList<>();
        appendTaskLabelHits(hits, response.getLabels());
        appendAudioSegmentHits(hits, response.getAudioSegments());
        appendImageSegmentHits(hits, response.getImageSegments());
        if (!hits.isEmpty()) {
            builder.hits(hits);
        }
        return builder.build();
    }

    /** 任务级标签命中（Label/SubLabel 汇总，Score 0~100） */
    private void appendTaskLabelHits(List<RiskHit> hits, TaskLabel[] labels) {
        if (labels == null) {
            return;
        }
        for (TaskLabel label : labels) {
            if (label == null || PASS_LABEL.equals(label.getLabel())) {
                continue;
            }
            List<String> labelPath = labelPathOf(label.getLabel(), label.getSubLabel());
            if (labelPath.isEmpty()) {
                continue;
            }
            hits.add(new RiskHit(
                    String.join(LABEL_PATH_SEPARATOR, labelPath),
                    labelPath,
                    toDouble(label.getScore()),
                    ConfidenceScale.PERCENTAGE,
                    HitSource.MACHINE,
                    null, null, Map.of()));
        }
    }

    /** 音频分段命中（HitFlag=1）；转写文本 Text 与片段地址 Url 不透传 */
    private void appendAudioSegmentHits(List<RiskHit> hits, AudioSegments[] segments) {
        if (segments == null) {
            return;
        }
        for (AudioSegments segment : segments) {
            AudioResult result = segment == null ? null : segment.getResult();
            if (result == null || !isSegmentHit(result.getHitFlag())
                    || PASS_LABEL.equals(result.getLabel())) {
                continue;
            }
            List<String> labelPath = labelPathOf(result.getLabel(), result.getSubLabel());
            if (labelPath.isEmpty()) {
                continue;
            }
            Map<String, Object> raw = new LinkedHashMap<>();
            if (segment.getOffsetTime() != null) {
                raw.put("offsetTime", segment.getOffsetTime());
            }
            hits.add(new RiskHit(
                    String.join(LABEL_PATH_SEPARATOR, labelPath),
                    labelPath,
                    toDouble(result.getScore()),
                    ConfidenceScale.PERCENTAGE,
                    HitSource.MACHINE,
                    null, null, raw));
        }
    }

    /** 截帧分段命中（HitFlag=1）；截图地址 Url 不透传 */
    private void appendImageSegmentHits(List<RiskHit> hits, ImageSegments[] segments) {
        if (segments == null) {
            return;
        }
        for (ImageSegments segment : segments) {
            ImageResult result = segment == null ? null : segment.getResult();
            if (result == null || !isSegmentHit(result.getHitFlag())
                    || PASS_LABEL.equals(result.getLabel())) {
                continue;
            }
            List<String> labelPath = labelPathOf(result.getLabel(), null);
            if (labelPath.isEmpty()) {
                continue;
            }
            Map<String, Object> raw = new LinkedHashMap<>();
            if (segment.getOffsetTime() != null) {
                raw.put("offsetTime", segment.getOffsetTime());
            }
            hits.add(new RiskHit(
                    String.join(LABEL_PATH_SEPARATOR, labelPath),
                    labelPath,
                    toDouble(result.getScore()),
                    ConfidenceScale.PERCENTAGE,
                    HitSource.MACHINE,
                    null, null, raw));
        }
    }

    /** 分段命中判定：HitFlag 缺失按命中处理（保守），显式非 1 视为未命中 */
    private static boolean isSegmentHit(Long hitFlag) {
        return hitFlag == null || hitFlag == 1L;
    }

    /** Label[/SubLabel] 标签路径元素（去空白） */
    private static List<String> labelPathOf(String label, String subLabel) {
        List<String> labelPath = new ArrayList<>();
        if (label != null && !label.isBlank()) {
            labelPath.add(label);
        }
        if (subLabel != null && !subLabel.isBlank()) {
            labelPath.add(subLabel);
        }
        return labelPath;
    }

    private AuditResult normalizeText(TextModerationResponse response, Map<String, Object> context) {
        AuditResult.Builder builder = baseBuilder(response.getRequestId(), response.getBizType(), context);
        RiskSuggestion suggestion = fromSuggestion(response.getSuggestion());
        if (suggestion == null) {
            suggestion = PASS_LABEL.equals(response.getLabel()) ? RiskSuggestion.PASS : RiskSuggestion.REJECT;
        }
        builder.suggestion(suggestion);
        if (RiskSuggestion.PASS != suggestion) {
            List<RiskHit> hits = new ArrayList<>();
            String label = response.getLabel();
            String subLabel = response.getSubLabel();
            Double score = toDouble(response.getScore());
            List<String> labels = new ArrayList<>();
            if (label != null && !label.isBlank()) {
                labels.add(label);
            }
            if (subLabel != null && !subLabel.isBlank()) {
                labels.add(subLabel);
            }
            if (!labels.isEmpty()) {
                // Keywords 是用户内容片段（敏感词原文），只能进 hit.raw
                // （raw 按约定不进日志/审计正文），不进 labels/evidence
                Map<String, Object> raw = response.getKeywords() == null || response.getKeywords().length == 0
                        ? Map.of() : Map.of("keywords", List.of(response.getKeywords()));
                hits.add(new RiskHit(
                        String.join(LABEL_PATH_SEPARATOR, labels),
                        labels,
                        score,
                        ConfidenceScale.PERCENTAGE,
                        HitSource.MACHINE,
                        null, null, raw));
            }
            builder.hits(hits);
        }
        return builder.build();
    }

    private AuditResult normalizeImage(ImageModerationResponse response, AuditSource source,
                                       Map<String, Object> context) {
        AuditResult.Builder builder = baseBuilder(response.getRequestId(), response.getBizType(), context)
                .source(source);
        RiskSuggestion suggestion = fromSuggestion(response.getSuggestion());
        if (suggestion == null) {
            suggestion = PASS_LABEL.equals(response.getLabel()) ? RiskSuggestion.PASS : RiskSuggestion.REJECT;
        }
        builder.suggestion(suggestion);
        if (RiskSuggestion.PASS == suggestion) {
            return builder.build();
        }
        List<RiskHit> hits = new ArrayList<>();
        appendLabelHits(hits, response.getLabelResults());
        appendObjectHits(hits, response.getObjectResults());
        appendOcrHits(hits, response.getOcrResults());
        if (hits.isEmpty()) {
            // 明细缺失时用顶层 Label/SubLabel 兜底
            List<String> labels = new ArrayList<>();
            if (response.getLabel() != null && !response.getLabel().isBlank()) {
                labels.add(response.getLabel());
            }
            if (response.getSubLabel() != null && !response.getSubLabel().isBlank()) {
                labels.add(response.getSubLabel());
            }
            if (!labels.isEmpty()) {
                hits.add(new RiskHit(
                        String.join(LABEL_PATH_SEPARATOR, labels),
                        labels,
                        toDouble(response.getScore()),
                        ConfidenceScale.PERCENTAGE,
                        HitSource.MACHINE,
                        null, null, Map.of()));
            }
        }
        return builder.hits(hits).build();
    }

    private void appendLabelHits(List<RiskHit> hits, LabelResult[] labelResults) {
        if (labelResults == null) {
            return;
        }
        for (LabelResult result : labelResults) {
            if (result == null || PASS_LABEL.equals(result.getLabel())) {
                continue;
            }
            List<String> labels = new ArrayList<>();
            if (result.getLabel() != null && !result.getLabel().isBlank()) {
                labels.add(result.getLabel());
            }
            if (result.getSubLabel() != null && !result.getSubLabel().isBlank()) {
                labels.add(result.getSubLabel());
            }
            if (labels.isEmpty()) {
                continue;
            }
            hits.add(new RiskHit(
                    String.join(LABEL_PATH_SEPARATOR, labels),
                    labels,
                    toDouble(result.getScore()),
                    ConfidenceScale.PERCENTAGE,
                    HitSource.MACHINE,
                    null, null, Map.of()));
        }
    }

    private void appendObjectHits(List<RiskHit> hits, ObjectResult[] objectResults) {
        if (objectResults == null) {
            return;
        }
        for (ObjectResult result : objectResults) {
            if (result == null || PASS_LABEL.equals(result.getLabel())) {
                continue;
            }
            List<String> labels = new ArrayList<>();
            if (result.getLabel() != null && !result.getLabel().isBlank()) {
                labels.add(result.getLabel());
            }
            if (result.getSubLabel() != null && !result.getSubLabel().isBlank()) {
                labels.add(result.getSubLabel());
            }
            if (result.getNames() != null) {
                labels.addAll(Arrays.stream(result.getNames())
                        .filter(name -> name != null && !name.isBlank())
                        .toList());
            }
            if (labels.isEmpty()) {
                continue;
            }
            Map<String, Object> raw = new LinkedHashMap<>();
            if (result.getDetails() != null) {
                List<Map<String, Object>> details = new ArrayList<>();
                for (ObjectDetail detail : result.getDetails()) {
                    if (detail == null) {
                        continue;
                    }
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("name", detail.getName());
                    item.put("value", detail.getValue());
                    item.put("score", detail.getScore());
                    if (detail.getLocation() != null) {
                        item.put("location", Map.of(
                                "x", nullSafe(detail.getLocation().getX()),
                                "y", nullSafe(detail.getLocation().getY()),
                                "width", nullSafe(detail.getLocation().getWidth()),
                                "height", nullSafe(detail.getLocation().getHeight())));
                    }
                    details.add(item);
                }
                if (!details.isEmpty()) {
                    raw.put("details", details);
                }
            }
            hits.add(new RiskHit(
                    String.join(LABEL_PATH_SEPARATOR, labels),
                    labels,
                    toDouble(result.getScore()),
                    ConfidenceScale.PERCENTAGE,
                    HitSource.MACHINE,
                    null, null, raw));
        }
    }

    /**
     * OCR 命中归一：只保留 label/keywords/坐标等元信息；OCR 文本原文
     * （用户内容）按安全约束丢弃，不进 evidence/raw
     */
    private void appendOcrHits(List<RiskHit> hits, OcrResult[] ocrResults) {
        if (ocrResults == null) {
            return;
        }
        for (OcrResult result : ocrResults) {
            if (result == null || PASS_LABEL.equals(result.getLabel())) {
                continue;
            }
            List<String> labels = new ArrayList<>();
            if (result.getLabel() != null && !result.getLabel().isBlank()) {
                labels.add(result.getLabel());
            }
            if (result.getSubLabel() != null && !result.getSubLabel().isBlank()) {
                labels.add(result.getSubLabel());
            }
            if (labels.isEmpty()) {
                continue;
            }
            hits.add(new RiskHit(
                    String.join(LABEL_PATH_SEPARATOR, labels),
                    labels,
                    toDouble(result.getScore()),
                    ConfidenceScale.PERCENTAGE,
                    HitSource.MACHINE,
                    null, null, Map.of()));
        }
    }

    private AuditResult.Builder baseBuilder(String requestId, String bizType, Map<String, Object> context) {
        Map<String, Object> safe = context == null ? Map.of() : context;
        Map<String, Object> raw = new LinkedHashMap<>();
        // raw 会经不可变拷贝（Map.copyOf 语义），null 值一律不放
        if (requestId != null) {
            raw.put("RequestId", requestId);
        }
        if (bizType != null) {
            raw.put("BizType", bizType);
        }
        return AuditResult.builder()
                .ok(true)
                .finalResult(true)
                .source(AuditSource.SYNC)
                .provider(provider())
                .product(TencentProviderConstants.PRODUCT)
                .service((String) safe.get(CONTEXT_SERVICE))
                .dataId((String) safe.get(CONTEXT_DATA_ID))
                .cost((Duration) safe.get(CONTEXT_COST))
                .requestId(requestId)
                .raw(raw);
    }

    /**
     * Suggestion 三档映射：Pass → PASS，Review → REVIEW，Block → REJECT；
     * 未知值返回 null（调用方回退 Label 判定）
     */
    static RiskSuggestion fromSuggestion(String suggestion) {
        if (suggestion == null) {
            return null;
        }
        return switch (suggestion.toLowerCase(Locale.ROOT)) {
            case "pass" -> RiskSuggestion.PASS;
            case "review" -> RiskSuggestion.REVIEW;
            case "block" -> RiskSuggestion.REJECT;
            default -> null;
        };
    }

    private static Double toDouble(Long score) {
        return score == null ? null : score.doubleValue();
    }

    private static Object nullSafe(Object value) {
        return value == null ? "" : value;
    }
}
