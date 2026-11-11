package io.github.wrqtec.content.safe.provider.aliyun.internal;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

import com.aliyun.green20220302.models.VideoModerationResultResponseBody;
import com.aliyun.green20220302.models.VideoModerationResponseBody;
import io.github.wrqtec.content.safe.api.AuditRequest;
import io.github.wrqtec.content.safe.api.AuditResult;
import io.github.wrqtec.content.safe.api.enums.AuditSource;
import io.github.wrqtec.content.safe.api.enums.ContentType;
import io.github.wrqtec.content.safe.api.exception.ContentSafeException;
import io.github.wrqtec.content.safe.api.exception.ProviderCallException;
import io.github.wrqtec.content.safe.api.exception.UnsupportedContentException;
import io.github.wrqtec.content.safe.api.exception.UnsupportedFeatureException;
import io.github.wrqtec.content.safe.api.util.Asserts;
import io.github.wrqtec.content.safe.model.AuditCapabilities;
import io.github.wrqtec.content.safe.model.AsyncTaskRef;
import io.github.wrqtec.content.safe.model.ProviderPlan;
import io.github.wrqtec.content.safe.provider.aliyun.AliyunProviderConstants;
import io.github.wrqtec.content.safe.spi.AuditOperation;
import lombok.extern.slf4j.Slf4j;

/**
 * 阿里云视频文件审核增强版（VideoModeration 提交 + VideoModerationResult 轮询）。
 * 视频审核全异步，仅接受公网可达 URL，base64/bytes 直接拒绝（铁律 §5.2 能力先行）。
 * 官方契约（help.aliyun.com/zh/document_detail/2505810，查阅 2026-11-11）：
 * 提交 ServiceParameters 携带 url（公网 URL）与 dataId；查询携带 taskId；
 * 业务码 200=完成、280=检测中、288=排队中；结果保留 24 小时，建议 30 秒间隔轮询。
 * 脱敏红线（铁律 §5.6）：结果中的截帧图 URL（tempUrl）与语音转写原文（text）
 * 不透传，hit.raw 只保留时间偏移、标签、置信分等元数据
 *
 * @author L.David
 */
@Slf4j
public class AliyunVideoAsyncOperation implements AuditOperation {

    /** serviceParameters 键（官方固定字段名） */
    static final String PARAM_URL = "url";
    static final String PARAM_DATA_ID = "dataId";
    static final String PARAM_TASK_ID = "taskId";

    /** 查询业务码：检测中 */
    static final int CODE_DETECTING = 280;

    /** 查询业务码：排队中（近线，尚未开始检测） */
    static final int CODE_QUEUED = 288;

    private final ProviderPlan plan;
    private final AuditCapabilities capabilities;
    private final GreenClientInvoker invoker;
    private final AliyunAuditResultNormalizer normalizer;

    public AliyunVideoAsyncOperation(ProviderPlan plan, GreenClientInvoker invoker,
                                     AliyunAuditResultNormalizer normalizer) {
        this.plan = Asserts.notNull(plan, "plan");
        this.invoker = Asserts.notNull(invoker, "invoker");
        this.normalizer = Asserts.notNull(normalizer, "normalizer");
        this.capabilities = AuditCapabilities.builder()
                .contentTypes(ContentType.VIDEO)
                .url(true)
                .asyncQuery(true)
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
        throw new UnsupportedFeatureException("aliyun video operation does not support synchronous audit; "
                + "use submitAsync/query");
    }

    @Override
    public AsyncTaskRef submitAsync(AuditRequest request) {
        Asserts.notNull(request, "request");
        if (!capabilities.supports(request)) {
            throw new UnsupportedContentException("aliyun video moderation only supports public url, "
                    + "got content type " + request.type() + "/" + request.content().type());
        }
        Map<String, String> serviceParameters = new LinkedHashMap<>();
        serviceParameters.put(PARAM_URL, request.content().value());
        if (request.dataId() != null) {
            serviceParameters.put(PARAM_DATA_ID, request.dataId());
        }
        try {
            VideoModerationResponseBody body = invoker.videoModeration(plan.service(), serviceParameters);
            AliyunErrorMapper.assertSuccess(body.getCode(), body.getMessage());
            String taskId = taskIdOf(body);
            log.debug("aliyun video submitted planKey={} requestId={}", plan.planKey(), body.getRequestId());
            return new AsyncTaskRef(taskId, request.dataId());
        } catch (ContentSafeException e) {
            throw e;
        } catch (Exception e) {
            throw AliyunErrorMapper.fromException(e);
        }
    }

    @Override
    public AuditResult query(AsyncTaskRef ref) {
        Asserts.notNull(ref, "ref");
        Asserts.notBlank(ref.providerTaskId(), "ref.providerTaskId");
        long startNanos = System.nanoTime();
        try {
            var body = invoker.videoModerationResult(plan.service(),
                    Map.of(PARAM_TASK_ID, ref.providerTaskId()));
            Integer code = body.getCode();
            if (code != null && (code == CODE_DETECTING || code == CODE_QUEUED)) {
                // 检测中/排队中：产出中间态结果（suggestion 置空，finalResult=false 由 core 继续轮询）
                return inProgress(body, ref);
            }
            AliyunErrorMapper.assertSuccess(code, body.getMessage());
            Duration cost = Duration.ofNanos(System.nanoTime() - startNanos);
            return normalizer.normalizeVideoAsyncResult(body,
                    AliyunAuditResultNormalizer.context(plan, ref.dataId(), cost));
        } catch (ContentSafeException e) {
            throw e;
        } catch (Exception e) {
            throw AliyunErrorMapper.fromException(e);
        }
    }

    /** 任务引用：data.taskId 为提交时返回的任务 ID */
    private static String taskIdOf(VideoModerationResponseBody body) {
        String taskId = body.getData() == null ? null : body.getData().getTaskId();
        if (taskId == null || taskId.isBlank()) {
            throw new ProviderCallException("aliyun video submit returned no taskId");
        }
        return taskId;
    }

    private AuditResult inProgress(VideoModerationResultResponseBody body, AsyncTaskRef ref) {
        Map<String, Object> raw = new LinkedHashMap<>();
        // code 在上方分支已判定非空；raw 走 Map.copyOf 拒 null，requestId 可空、非空才记录
        raw.put("code", body.getCode());
        if (body.getRequestId() != null) {
            raw.put("requestId", body.getRequestId());
        }
        return AuditResult.builder()
                .ok(true)
                .suggestion(null)
                .finalResult(false)
                .source(AuditSource.ASYNC_QUERY)
                .provider(AliyunProviderConstants.PROVIDER)
                .product(AliyunProviderConstants.PRODUCT)
                .service(plan.service())
                .dataId(ref.dataId())
                .requestId(body.getRequestId())
                .raw(raw)
                .build();
    }
}
