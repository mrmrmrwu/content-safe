package io.github.wrqtec.content.safe.provider.tencent.internal;

import java.time.Duration;
import java.util.Base64;

import com.tencentcloudapi.ims.v20201229.models.Device;
import com.tencentcloudapi.ims.v20201229.models.ImageModerationRequest;
import com.tencentcloudapi.ims.v20201229.models.ImageModerationResponse;
import com.tencentcloudapi.ims.v20201229.models.User;
import io.github.wrqtec.content.safe.api.AuditContext;
import io.github.wrqtec.content.safe.api.AuditRequest;
import io.github.wrqtec.content.safe.api.AuditResult;
import io.github.wrqtec.content.safe.api.enums.ContentRefType;
import io.github.wrqtec.content.safe.api.enums.ContentType;
import io.github.wrqtec.content.safe.api.exception.ContentSafeException;
import io.github.wrqtec.content.safe.api.exception.UnsupportedContentException;
import io.github.wrqtec.content.safe.api.util.Asserts;
import io.github.wrqtec.content.safe.model.AuditCapabilities;
import io.github.wrqtec.content.safe.model.ProviderPlan;
import io.github.wrqtec.content.safe.provider.tencent.TencentProviderConstants;
import io.github.wrqtec.content.safe.spi.AuditOperation;
import lombok.extern.slf4j.Slf4j;

/**
 * 腾讯云图片同步审核（IMS ImageModeration）。支持公网 URL（FileUrl）与
 * Base64（FileContent，bytes 内部编码）；GIF 截帧参数与 AIGC 识别类型
 * 由配置驱动（铁律 §5.2 能力先行，不支持的内容类型在调用前拒绝）
 *
 * @author L.David
 */
@Slf4j
public class TencentImageModerationOperation implements AuditOperation {

    private final ProviderPlan plan;
    private final String type;
    private final Integer gifInterval;
    private final Integer gifMaxFrames;
    private final AuditCapabilities capabilities;
    private final TencentClientInvoker invoker;
    private final TencentAuditResultNormalizer normalizer;

    /**
     * @param plan         操作计划（service 为 BizType）
     * @param type         IMS 审核类型（{@code IMAGE} / {@code IMAGE_AIGC}），null 走默认
     * @param gifInterval  GIF 截帧间隔，null 不设置
     * @param gifMaxFrames GIF 最大截帧数，null 不设置
     */
    public TencentImageModerationOperation(ProviderPlan plan, String type,
                                           Integer gifInterval, Integer gifMaxFrames,
                                           TencentClientInvoker invoker,
                                           TencentAuditResultNormalizer normalizer) {
        this.plan = Asserts.notNull(plan, "plan");
        this.type = type;
        this.gifInterval = gifInterval;
        this.gifMaxFrames = gifMaxFrames;
        this.invoker = Asserts.notNull(invoker, "invoker");
        this.normalizer = Asserts.notNull(normalizer, "normalizer");
        this.capabilities = AuditCapabilities.builder()
                .contentTypes(ContentType.IMAGE)
                .sync(true)
                .url(true)
                .base64(true)
                .bytes(true)
                .aigcDetect(true)
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
        Asserts.notNull(request, "request");
        if (!capabilities.supports(request)) {
            throw new UnsupportedContentException("tencent image moderation supports url/base64/bytes, got "
                    + request.type() + "/" + request.content().type());
        }
        ImageModerationRequest sdkRequest = new ImageModerationRequest();
        sdkRequest.setBizType(plan.service());
        applyContent(request, sdkRequest);
        if (request.dataId() != null) {
            sdkRequest.setDataId(request.dataId());
        }
        String effectiveType = request.options().enableAigcDetect() == Boolean.TRUE
                ? TencentProviderConstants.IMAGE_TYPE_AIGC
                : type;
        if (effectiveType != null) {
            sdkRequest.setType(effectiveType);
        }
        if (gifInterval != null) {
            sdkRequest.setInterval(gifInterval.longValue());
        }
        if (gifMaxFrames != null) {
            sdkRequest.setMaxFrames(gifMaxFrames.longValue());
        }
        applyContext(request.context(), sdkRequest);
        long startNanos = System.nanoTime();
        try {
            ImageModerationResponse response = invoker.imageModeration(sdkRequest);
            log.debug("tencent image moderation done planKey={} requestId={}", plan.planKey(),
                    response.getRequestId());
            Duration cost = Duration.ofNanos(System.nanoTime() - startNanos);
            return normalizer.normalize(response,
                    TencentAuditResultNormalizer.context(plan, request.dataId(), cost));
        } catch (ContentSafeException e) {
            throw e;
        } catch (Exception e) {
            throw TencentErrorMapper.fromException(e);
        }
    }

    /** 内容装配：URL → FileUrl；BASE64 → FileContent（限长校验）；BYTES → Base64 编码后同左 */
    private void applyContent(AuditRequest request, ImageModerationRequest sdkRequest) {
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
                    "tencent image moderation does not support content type " + contentType);
        }
    }

    private void assertBase64Size(long base64Length) {
        if (base64Length > TencentLimits.maxBase64Bytes()) {
            throw new UnsupportedContentException("tencent image moderation FileContent(base64) supports at most "
                    + TencentLimits.maxBase64Bytes() + " bytes, got " + base64Length);
        }
    }

    /** AuditContext → User/Device 风控字段映射（均为可选，缺失不设置） */
    private void applyContext(AuditContext context, ImageModerationRequest sdkRequest) {
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
