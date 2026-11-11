package io.github.wrqtec.content.safe.provider.tencent.internal;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

import com.tencentcloudapi.common.AbstractModel;
import com.tencentcloudapi.vm.v20210922.models.CreateVideoModerationTaskRequest;
import com.tencentcloudapi.vm.v20210922.models.CreateVideoModerationTaskResponse;
import com.tencentcloudapi.vm.v20210922.models.DescribeTaskDetailRequest;
import com.tencentcloudapi.vm.v20210922.models.DescribeTaskDetailResponse;
import com.tencentcloudapi.vm.v20210922.models.StorageInfo;
import com.tencentcloudapi.vm.v20210922.models.TaskInput;
import com.tencentcloudapi.vm.v20210922.models.TaskResult;
import io.github.wrqtec.content.safe.api.AuditRequest;
import io.github.wrqtec.content.safe.api.AuditResult;
import io.github.wrqtec.content.safe.api.CallbackRequest;
import io.github.wrqtec.content.safe.api.CallbackResult;
import io.github.wrqtec.content.safe.api.enums.ContentRefType;
import io.github.wrqtec.content.safe.api.enums.ContentType;
import io.github.wrqtec.content.safe.api.exception.ContentSafeException;
import io.github.wrqtec.content.safe.api.exception.ProviderCallException;
import io.github.wrqtec.content.safe.api.exception.UnsupportedContentException;
import io.github.wrqtec.content.safe.api.exception.UnsupportedFeatureException;
import io.github.wrqtec.content.safe.api.util.Asserts;
import io.github.wrqtec.content.safe.model.AuditCapabilities;
import io.github.wrqtec.content.safe.model.AsyncTaskRef;
import io.github.wrqtec.content.safe.model.ProviderPlan;
import io.github.wrqtec.content.safe.provider.tencent.TencentProviderConstants;
import io.github.wrqtec.content.safe.spi.AuditOperation;
import lombok.extern.slf4j.Slf4j;

/**
 * 腾讯云视频异步审核（VM CreateVideoModerationTask / DescribeTaskDetail）。
 * 官方契约（cloud.tencent.com/document/product/1265/79255、80018、80019，
 * 查阅 2026-11-11）：提交返回 Results[].TaskId；任务状态 Status ∈
 * PENDING/RUNNING/ERROR/FINISH/CANCELLED；FINISH 前轮询查询，也可配置
 * CallbackUrl 由云侧推送（报文与 DescribeTaskDetail 响应同构，含 TaskId）。
 * 仅支持公网 URL 内容。任务级 Labels 与音/图分段命中映射为 hits；
 * 分段时间戳存 raw.offsetTime（点播为偏移秒、直播为时间戳字符串）；
 * 语音转写（AudioResult.Text）、片段视频/音频 URL（VideoUrl/AudioUrl）、
 * 命中片段（HitSnippetInfos）与截帧图 URL（ImageResult.Url）均属用户
 * 内容或云侧临时地址，绝不透传
 *
 * @author L.David
 */
@Slf4j
public class TencentVideoAsyncOperation implements AuditOperation {

    /** 提交响应成功码（TaskResult.Code，"OK" 表示受理成功） */
    private static final String CODE_OK = "OK";

    /** 任务中间态：排队中 */
    private static final String STATUS_PENDING = "PENDING";

    /** 任务中间态：执行中 */
    private static final String STATUS_RUNNING = "RUNNING";

    /** 任务终态：完成 */
    private static final String STATUS_FINISH = "FINISH";

    /** 任务终态：失败 */
    private static final String STATUS_ERROR = "ERROR";

    /** 任务终态：取消 */
    private static final String STATUS_CANCELLED = "CANCELLED";

    private final ProviderPlan plan;
    private final String callbackUrl;
    private final AuditCapabilities capabilities;
    private final TencentClientInvoker invoker;
    private final TencentAuditResultNormalizer normalizer;

    /**
     * @param plan        操作计划（service 为 BizType）
     * @param callbackUrl 回调地址（extra callback-url），null 时仅轮询查询
     * @param invoker     SDK 调用接缝
     * @param normalizer  结果归一化
     */
    public TencentVideoAsyncOperation(ProviderPlan plan, String callbackUrl, TencentClientInvoker invoker,
                                      TencentAuditResultNormalizer normalizer) {
        this.plan = Asserts.notNull(plan, "plan");
        this.callbackUrl = callbackUrl;
        this.invoker = Asserts.notNull(invoker, "invoker");
        this.normalizer = Asserts.notNull(normalizer, "normalizer");
        this.capabilities = AuditCapabilities.builder()
                .contentTypes(ContentType.VIDEO)
                .url(true)
                .asyncQuery(true)
                .asyncCallback(callbackUrl != null && !callbackUrl.isBlank())
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
        throw new UnsupportedFeatureException("tencent video moderation is asynchronous only; use submitAsync/query");
    }

    /**
     * 提交异步审核任务。任务关联键为云侧返回的 TaskId（回调报文与查询
     * 接口均携带），请求 dataId 透传给云侧作业务关联，允许为 null
     *
     * @throws UnsupportedContentException 内容非公网 URL
     */
    @Override
    public AsyncTaskRef submitAsync(AuditRequest request) {
        Asserts.notNull(request, "request");
        if (!capabilities.supports(request)) {
            throw new UnsupportedContentException("tencent video moderation supports url only, got "
                    + request.type() + "/" + request.content().type());
        }
        CreateVideoModerationTaskRequest sdkRequest = new CreateVideoModerationTaskRequest();
        sdkRequest.setBizType(plan.service());
        TaskInput input = new TaskInput();
        input.setDataId(request.dataId());
        StorageInfo storage = new StorageInfo();
        storage.setType("URL");
        storage.setUrl(request.content().value());
        input.setInput(storage);
        sdkRequest.setTasks(new TaskInput[]{input});
        if (callbackUrl != null && !callbackUrl.isBlank()) {
            sdkRequest.setCallbackUrl(callbackUrl);
        }
        try {
            CreateVideoModerationTaskResponse response = invoker.createVideoModerationTask(sdkRequest);
            log.debug("tencent video task accepted planKey={} dataId={}", plan.planKey(), request.dataId());
            return new AsyncTaskRef(taskIdOf(response), request.dataId());
        } catch (ContentSafeException e) {
            throw e;
        } catch (Exception e) {
            throw TencentErrorMapper.fromException(e);
        }
    }

    /**
     * 轮询任务结果。PENDING/RUNNING 返回 in-progress（finalResult=false）；
     * FINISH 返回终态归一化；ERROR/CANCELLED 上抛失败（带云侧错误类型，
     * 描述经脱敏截断）；未知状态按中间态继续轮询（向前兼容官方新增状态）
     */
    @Override
    public AuditResult query(AsyncTaskRef ref) {
        Asserts.notBlank(ref.providerTaskId(), "providerTaskId");
        long startNanos = System.nanoTime();
        DescribeTaskDetailRequest sdkRequest = new DescribeTaskDetailRequest();
        sdkRequest.setTaskId(ref.providerTaskId());
        // 全量分段（默认仅输出部分片段），用于完整的时间点命中映射
        sdkRequest.setShowAllSegments(Boolean.TRUE);
        try {
            DescribeTaskDetailResponse response = invoker.describeTaskDetail(sdkRequest);
            Duration cost = Duration.ofNanos(System.nanoTime() - startNanos);
            String status = response.getStatus();
            if (STATUS_PENDING.equals(status) || STATUS_RUNNING.equals(status)) {
                return normalizer.inProgressVideo(response, status,
                        TencentAuditResultNormalizer.context(plan, ref.dataId(), cost));
            }
            if (!STATUS_FINISH.equals(status)) {
                throw taskFailed(response, status);
            }
            return normalizer.normalizeVideoQuery(response,
                    TencentAuditResultNormalizer.context(plan, ref.dataId(), cost));
        } catch (ContentSafeException e) {
            throw e;
        } catch (Exception e) {
            throw TencentErrorMapper.fromException(e);
        }
    }

    /**
     * 回调处理：报文与 DescribeTaskDetail 响应同构，反序列化后复用同一套
     * 归一化；任务关联键为报文 TaskId（缺失回退 DataId）。非终态报文拒收
     */
    @Override
    public CallbackResult handleCallback(CallbackRequest request) {
        DescribeTaskDetailResponse response = parseCallback(request.rawBody());
        if (response == null) {
            return CallbackResult.rejected("invalid tencent video callback payload");
        }
        String taskId = firstNonBlank(response.getTaskId(), response.getDataId());
        if (taskId == null) {
            return CallbackResult.rejected("tencent video callback missing TaskId/DataId (correlation key)");
        }
        String status = response.getStatus();
        if (STATUS_PENDING.equals(status) || STATUS_RUNNING.equals(status)) {
            return CallbackResult.rejected("tencent video callback status not terminal: " + status);
        }
        if (!STATUS_FINISH.equals(status)) {
            // 异常终态报文同样走拒收（进死信事件），不抛异常中断派发
            return CallbackResult.rejected("tencent video task terminated abnormally: status=" + status);
        }
        AuditResult result = normalizer.normalizeVideoCallback(response,
                TencentAuditResultNormalizer.context(plan, firstNonBlank(response.getDataId(), taskId), Duration.ZERO));
        return CallbackResult.accepted(taskId, result);
    }

    /** 任务受理结果提取：Results 为空或 Code 非 OK 视为受理失败 */
    private String taskIdOf(CreateVideoModerationTaskResponse response) {
        TaskResult[] results = response.getResults();
        if (results == null || results.length == 0) {
            throw new ProviderCallException("tencent video submit returned no task result");
        }
        TaskResult result = results[0];
        if (!CODE_OK.equals(result.getCode())) {
            throw new ProviderCallException("tencent video submit failed: code=" + result.getCode()
                    + ", message=" + TencentErrorMapper.sanitize(result.getMessage()));
        }
        if (result.getTaskId() == null || result.getTaskId().isBlank()) {
            throw new ProviderCallException("tencent video submit returned no taskId");
        }
        return result.getTaskId();
    }

    /** 异常终态（ERROR/CANCELLED）→ 调用异常；描述经脱敏，不含任务内容 */
    private ProviderCallException taskFailed(DescribeTaskDetailResponse response, String status) {
        StringBuilder message = new StringBuilder("tencent video task terminated abnormally: status=").append(status);
        if (response.getErrorType() != null && !response.getErrorType().isBlank()) {
            message.append(", errorType=").append(response.getErrorType());
        }
        if (response.getErrorDescription() != null && !response.getErrorDescription().isBlank()) {
            message.append(", errorDescription=").append(TencentErrorMapper.sanitize(response.getErrorDescription()));
        }
        return new ProviderCallException(message.toString());
    }

    /** 回调报文反序列化；解析失败返回 null（拒收，不抛异常中断派发） */
    private DescribeTaskDetailResponse parseCallback(String rawBody) {
        if (rawBody == null || rawBody.isBlank()) {
            return null;
        }
        try {
            return AbstractModel.fromJsonString(rawBody, DescribeTaskDetailResponse.class);
        } catch (Exception e) {
            log.warn("tencent video callback payload parse failed: {}", e.getClass().getSimpleName());
            return null;
        }
    }

    private static String firstNonBlank(String primary, String fallback) {
        if (primary != null && !primary.isBlank()) {
            return primary;
        }
        if (fallback != null && !fallback.isBlank()) {
            return fallback;
        }
        return null;
    }
}
