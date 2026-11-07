package io.github.wrqtec.content.safe.provider.tencent.internal;

import java.time.Duration;
import java.util.Base64;

import com.tencentcloudapi.common.AbstractModel;
import com.tencentcloudapi.ims.v20201229.models.CreateImageModerationAsyncTaskRequest;
import com.tencentcloudapi.ims.v20201229.models.CreateImageModerationAsyncTaskResponse;
import com.tencentcloudapi.ims.v20201229.models.Device;
import com.tencentcloudapi.ims.v20201229.models.ImageModerationResponse;
import com.tencentcloudapi.ims.v20201229.models.User;
import io.github.wrqtec.content.safe.api.AuditContext;
import io.github.wrqtec.content.safe.api.AuditRequest;
import io.github.wrqtec.content.safe.api.AuditResult;
import io.github.wrqtec.content.safe.api.CallbackRequest;
import io.github.wrqtec.content.safe.api.CallbackResult;
import io.github.wrqtec.content.safe.api.enums.ContentRefType;
import io.github.wrqtec.content.safe.api.enums.ContentType;
import io.github.wrqtec.content.safe.api.exception.ConfigInvalidException;
import io.github.wrqtec.content.safe.api.exception.ContentSafeException;
import io.github.wrqtec.content.safe.api.exception.InvalidRequestException;
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
 * 腾讯云图片异步审核（IMS CreateImageModerationAsyncTask）。官方契约
 * （cloud.tencent.com/document/product/1125/87021，查阅 2026-11-07）：
 * 提交时 CallbackUrl 必填，审核结果以同步 ImageModeration 输出格式经回调推送，
 * 无主动查询接口；回调报文不含任务 ID，任务关联依赖 DataId。API 本身
 * 无签名机制，回调防伪由 {@link TencentCallbackVerifier} 结构校验兜底
 *
 * @author L.David
 */
@Slf4j
public class TencentImageAsyncOperation implements AuditOperation {

    private final ProviderPlan plan;
    private final Integer gifInterval;
    private final Integer gifMaxFrames;
    private final String callbackUrl;
    private final AuditCapabilities capabilities;
    private final TencentClientInvoker invoker;
    private final TencentAuditResultNormalizer normalizer;

    /**
     * @param plan         操作计划（service 为 BizType）
     * @param gifInterval  GIF 截帧间隔，null 不设置
     * @param gifMaxFrames GIF 最大截帧数，null 不设置
     * @param callbackUrl  回调地址（extra callback-url），官方必填
     */
    public TencentImageAsyncOperation(ProviderPlan plan, Integer gifInterval, Integer gifMaxFrames,
                                      String callbackUrl, TencentClientInvoker invoker,
                                      TencentAuditResultNormalizer normalizer) {
        this.plan = Asserts.notNull(plan, "plan");
        this.gifInterval = gifInterval;
        this.gifMaxFrames = gifMaxFrames;
        this.callbackUrl = callbackUrl;
        this.invoker = Asserts.notNull(invoker, "invoker");
        this.normalizer = Asserts.notNull(normalizer, "normalizer");
        this.capabilities = AuditCapabilities.builder()
                .contentTypes(ContentType.IMAGE)
                .url(true)
                .base64(true)
                .bytes(true)
                .asyncCallback(callbackUrl != null && !callbackUrl.isBlank())
                .maxUrlSize(TencentLimits.maxUrlBytes())
                .maxBase64Size(TencentLimits.maxBase64Bytes())
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
        throw new UnsupportedFeatureException("tencent image async task supports submitAsync/callback only");
    }

    /**
     * 受理异步审核任务。任务无独立 ID，回调关联键为 DataId，故
     * providerTaskId 取提交响应回显的 DataId（缺失回退请求 dataId）
     *
     * @throws InvalidRequestException 请求缺 dataId（回调关联必需）
     * @throws ConfigInvalidException  callback-url 未配置
     */
    @Override
    public AsyncTaskRef submitAsync(AuditRequest request) {
        Asserts.notNull(request, "request");
        if (!capabilities.supports(request)) {
            throw new UnsupportedContentException("tencent image async task supports url/base64/bytes, got "
                    + request.type() + "/" + request.content().type());
        }
        if (callbackUrl == null || callbackUrl.isBlank()) {
            throw new ConfigInvalidException("tencent image async task requires extra '"
                    + TencentProviderConstants.EXTRA_CALLBACK_URL + "' (CallbackUrl)");
        }
        if (request.dataId() == null || request.dataId().isBlank()) {
            throw new InvalidRequestException("tencent image async task requires dataId "
                    + "(callback correlation key)");
        }
        CreateImageModerationAsyncTaskRequest sdkRequest = new CreateImageModerationAsyncTaskRequest();
        sdkRequest.setBizType(plan.service());
        applyContent(request, sdkRequest);
        sdkRequest.setDataId(request.dataId());
        sdkRequest.setCallbackUrl(callbackUrl);
        if (gifInterval != null) {
            sdkRequest.setInterval(gifInterval.longValue());
        }
        if (gifMaxFrames != null) {
            sdkRequest.setMaxFrames(gifMaxFrames.longValue());
        }
        applyContext(request.context(), sdkRequest);
        try {
            CreateImageModerationAsyncTaskResponse response = invoker.createImageModerationAsyncTask(sdkRequest);
            log.debug("tencent image async task accepted planKey={} dataId={}", plan.planKey(), request.dataId());
            String taskKey = response.getDataId() != null && !response.getDataId().isBlank()
                    ? response.getDataId() : request.dataId();
            return new AsyncTaskRef(taskKey, request.dataId());
        } catch (ContentSafeException e) {
            throw e;
        } catch (Exception e) {
            throw TencentErrorMapper.fromException(e);
        }
    }

    /** 官方无主动查询接口（终态以回调推送为准） */
    @Override
    public AuditResult query(AsyncTaskRef ref) {
        throw new UnsupportedFeatureException("tencent image async task has no query API; "
                + "terminal result arrives via callback");
    }

    /**
     * 回调处理：报文与同步 ImageModeration 输出同构，反序列化后复用
     * 同一套归一化；taskId 取 DataId 与保存的任务引用关联
     */
    @Override
    public CallbackResult handleCallback(CallbackRequest request) {
        ImageModerationResponse response = parseCallback(request.rawBody());
        if (response == null) {
            return CallbackResult.rejected("invalid tencent callback payload");
        }
        if (response.getDataId() == null || response.getDataId().isBlank()) {
            return CallbackResult.rejected("tencent callback missing DataId (correlation key)");
        }
        AuditResult result = normalizer.normalizeImageCallback(response,
                TencentAuditResultNormalizer.context(plan, response.getDataId(), Duration.ZERO));
        return CallbackResult.accepted(response.getDataId(), result);
    }

    /** 回调报文反序列化；解析失败返回 null（拒收，不抛异常中断派发） */
    private ImageModerationResponse parseCallback(String rawBody) {
        if (rawBody == null || rawBody.isBlank()) {
            return null;
        }
        try {
            return AbstractModel.fromJsonString(rawBody, ImageModerationResponse.class);
        } catch (Exception e) {
            log.warn("tencent callback payload parse failed: {}", e.getClass().getSimpleName());
            return null;
        }
    }

    /** 内容装配：URL → FileUrl；BASE64 → FileContent；BYTES → Base64 编码后同左 */
    private void applyContent(AuditRequest request, CreateImageModerationAsyncTaskRequest sdkRequest) {
        ContentRefType contentType = request.content().type();
        switch (contentType) {
            case URL -> sdkRequest.setFileUrl(request.content().value());
            case BASE64 -> {
                String base64 = request.content().value();
                assertBase64Size(base64.length());
                sdkRequest.setFileContent(base64);
            }
            case BYTES -> {
                byte[] bytes = request.content().bytes();
                String base64 = Base64.getEncoder().encodeToString(bytes);
                assertBase64Size(base64.length());
                sdkRequest.setFileContent(base64);
            }
            default -> throw new UnsupportedContentException(
                    "tencent image async task does not support content type " + contentType);
        }
    }

    private void assertBase64Size(long base64Length) {
        if (base64Length > TencentLimits.maxBase64Bytes()) {
            throw new UnsupportedContentException("tencent image async task FileContent(base64) supports at most "
                    + TencentLimits.maxBase64Bytes() + " bytes, got " + base64Length);
        }
    }

    /** AuditContext → User/Device 风控字段映射（均为可选，缺失不设置） */
    private void applyContext(AuditContext context, CreateImageModerationAsyncTaskRequest sdkRequest) {
        if (context == null) {
            return;
        }
        if (context.userId() != null && !context.userId().isBlank()) {
            User user = new User();
            user.setUserId(context.userId());
            sdkRequest.setUser(user);
        }
        boolean hasDevice = false;
        Device device = new Device();
        if (context.deviceId() != null && !context.deviceId().isBlank()) {
            device.setDeviceId(context.deviceId());
            hasDevice = true;
        }
        if (context.ip() != null && !context.ip().isBlank()) {
            device.setIp(context.ip());
            hasDevice = true;
        }
        if (hasDevice) {
            sdkRequest.setDevice(device);
        }
    }
}
