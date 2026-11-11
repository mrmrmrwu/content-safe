package io.github.wrqtec.content.safe.provider.baidu.internal;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import io.github.wrqtec.content.safe.api.AuditRequest;
import io.github.wrqtec.content.safe.api.AuditResult;
import io.github.wrqtec.content.safe.api.CallbackRequest;
import io.github.wrqtec.content.safe.api.CallbackResult;
import io.github.wrqtec.content.safe.api.enums.AuditSource;
import io.github.wrqtec.content.safe.api.enums.ContentType;
import io.github.wrqtec.content.safe.api.exception.ContentSafeException;
import io.github.wrqtec.content.safe.api.exception.ProviderCallException;
import io.github.wrqtec.content.safe.api.exception.UnsupportedContentException;
import io.github.wrqtec.content.safe.api.exception.UnsupportedFeatureException;
import io.github.wrqtec.content.safe.api.util.Asserts;
import io.github.wrqtec.content.safe.model.AsyncTaskRef;
import io.github.wrqtec.content.safe.model.AuditCapabilities;
import io.github.wrqtec.content.safe.model.ProviderPlan;
import io.github.wrqtec.content.safe.provider.baidu.BaiduProviderConstants;
import io.github.wrqtec.content.safe.spi.AuditOperation;
import lombok.extern.slf4j.Slf4j;

/**
 * 百度长视频审核异步操作（提交 + 轮询拉取 + 可选回调）
 *
 * <p>仅接受公网 URL 输入（官方 Format=URL）。提交时 extId 固定取业务侧 dataId
 * （appId+extId 组合幂等键，重复提交返回 282005）；配置 notice-url 后启用回调能力位，
 * 回调报文与拉取响应同构。处理中语义：ret="0" 且 conclusionType 缺省（官方文档未明示，
 * 按此实现并记录于设计文档实现勘误）；conclusionType=4（审核失败）按调用失败抛
 * {@link ProviderCallException}，任务标记 FAILED（与腾讯 Status=ERROR 同语义）
 *
 * @author L.David
 */
@Slf4j
public class BaiduVideoAsyncOperation implements AuditOperation {

    private static final Gson GSON = new Gson();

    /** 结论类型：审核失败 */
    private static final int CONCLUSION_ERROR = 4;

    private final ProviderPlan plan;
    private final String noticeUrl;
    private final String subEvents;
    private final BaiduCensorClient client;
    private final BaiduAuditResultNormalizer normalizer;
    private final AuditCapabilities capabilities;

    public BaiduVideoAsyncOperation(ProviderPlan plan, String noticeUrl, String subEvents,
                                    BaiduCensorClient client, BaiduAuditResultNormalizer normalizer) {
        this.plan = Asserts.notNull(plan, "plan");
        this.noticeUrl = noticeUrl;
        this.subEvents = subEvents;
        this.client = Asserts.notNull(client, "client");
        this.normalizer = Asserts.notNull(normalizer, "normalizer");
        this.capabilities = AuditCapabilities.builder()
                .contentTypes(ContentType.VIDEO)
                .url(true)
                .asyncQuery(true)
                .asyncCallback(noticeUrl != null && !noticeUrl.isBlank())
                .build();
    }

    @Override
    public ProviderPlan plan() {
        return plan;
    }

    @Override
    public AuditCapabilities capabilities() {
        return capabilities;
    }

    @Override
    public AuditResult audit(AuditRequest request) {
        throw new UnsupportedFeatureException(
                "baidu video censor is asynchronous only; use submitAsync/query");
    }

    @Override
    public AsyncTaskRef submitAsync(AuditRequest request) {
        Asserts.notNull(request, "request");
        if (!capabilities.supports(request)) {
            throw new UnsupportedContentException(
                    "baidu video censor supports url only, got "
                            + request.type() + "/" + request.content().type());
        }
        try {
            BaiduVideoSubmitResponse response = client.videoSubmit(
                    request.content().value(), request.dataId(), plan.service(), noticeUrl, subEvents);
            Long taskId = response.getData() == null ? null : response.getData().getTaskId();
            if (taskId == null) {
                throw new ProviderCallException("baidu video submit returned no taskId");
            }
            log.debug("baidu video submit done, planKey={}, taskId={}", plan.planKey(), taskId);
            return new AsyncTaskRef(String.valueOf(taskId), request.dataId());
        } catch (ContentSafeException e) {
            throw e;
        } catch (Exception e) {
            throw BaiduErrorMapper.fromException(e);
        }
    }

    @Override
    public AuditResult query(AsyncTaskRef ref) {
        Asserts.notNull(ref, "ref");
        Asserts.notBlank(ref.providerTaskId(), "ref.providerTaskId");
        long startNanos = System.nanoTime();
        try {
            BaiduVideoPullResponse response = client.videoPull(ref.providerTaskId());
            BaiduVideoPullResponse.Data data = response.getData();
            if (data == null) {
                throw new ProviderCallException("baidu video pull returned no data");
            }
            Duration cost = Duration.ofNanos(System.nanoTime() - startNanos);
            if (data.getErrorCode() != null) {
                throw new ProviderCallException("baidu video task failed: code=" + data.getErrorCode()
                        + " msg=" + BaiduErrorMapper.sanitize(data.getErrorMsg()));
            }
            if (!data.hasConclusion()) {
                return inProgress(ref, response.getLogId(), cost);
            }
            if (data.getConclusionType() == CONCLUSION_ERROR) {
                throw new ProviderCallException("baidu video task failed: type=" + data.getConclusionType()
                        + " msg=" + BaiduErrorMapper.sanitize(data.getErrorMsg()));
            }
            return normalizer.normalizeVideoPull(data, response.getLogId(),
                    BaiduAuditResultNormalizer.context(plan, ref.dataId(), cost));
        } catch (ContentSafeException e) {
            throw e;
        } catch (Exception e) {
            throw BaiduErrorMapper.fromException(e);
        }
    }

    @Override
    public CallbackResult handleCallback(CallbackRequest request) {
        BaiduVideoCallbackPayload payload = parseCallback(request.rawBody());
        if (payload == null) {
            return CallbackResult.rejected("invalid baidu video callback payload");
        }
        String taskId = payload.getTaskId() == null
                ? payload.getExtId()
                : String.valueOf(payload.getTaskId());
        if (taskId == null || taskId.isBlank()) {
            return CallbackResult.rejected("baidu video callback missing taskId/extId (correlation key)");
        }
        BaiduVideoCallbackPayload.Result result = payload.getResult();
        if (result == null) {
            return CallbackResult.rejected("baidu video callback missing result");
        }
        if (result.getErrorCode() != null && result.getErrorCode() != 0) {
            return CallbackResult.rejected("baidu video task failed: code=" + result.getErrorCode()
                    + " msg=" + BaiduErrorMapper.sanitize(result.getErrorMsg()));
        }
        if (result.getConclusionType() == null) {
            return CallbackResult.rejected("baidu video callback result not terminal");
        }
        if (result.getConclusionType() == CONCLUSION_ERROR) {
            return CallbackResult.rejected("baidu video task failed: type=" + result.getConclusionType()
                    + " msg=" + BaiduErrorMapper.sanitize(result.getErrorMsg()));
        }
        AuditResult auditResult = normalizer.normalizeVideoCallback(result,
                BaiduAuditResultNormalizer.context(plan, payload.getExtId(), Duration.ZERO));
        return CallbackResult.accepted(taskId, auditResult);
    }

    /**
     * 处理中：ret="0" 且无结论。suggestion 必须显式置 null
     * （AuditResult.builder 默认 PASS/finalResult=true）
     */
    private AuditResult inProgress(AsyncTaskRef ref, Long logId, Duration cost) {
        Map<String, Object> raw = new LinkedHashMap<>();
        raw.put("taskId", ref.providerTaskId());
        if (logId != null) {
            raw.put("logId", logId);
        }
        return AuditResult.builder()
                .ok(true)
                .suggestion(null)
                .finalResult(false)
                .source(AuditSource.ASYNC_QUERY)
                .provider(BaiduProviderConstants.PROVIDER)
                .product(BaiduProviderConstants.PRODUCT)
                .service(plan.service())
                .dataId(ref.dataId())
                .cost(cost)
                .raw(raw)
                .build();
    }

    private BaiduVideoCallbackPayload parseCallback(String rawBody) {
        if (rawBody == null || rawBody.isBlank()) {
            return null;
        }
        try {
            return GSON.fromJson(rawBody, BaiduVideoCallbackPayload.class);
        } catch (JsonSyntaxException e) {
            log.warn("baidu video callback parse failed: payload is not valid json");
            return null;
        }
    }
}
