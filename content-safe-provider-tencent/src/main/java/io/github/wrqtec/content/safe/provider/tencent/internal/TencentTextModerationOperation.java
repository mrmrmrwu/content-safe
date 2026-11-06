package io.github.wrqtec.content.safe.provider.tencent.internal;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;

import com.tencentcloudapi.tms.v20201229.models.Device;
import com.tencentcloudapi.tms.v20201229.models.TextModerationRequest;
import com.tencentcloudapi.tms.v20201229.models.TextModerationResponse;
import com.tencentcloudapi.tms.v20201229.models.User;
import io.github.wrqtec.content.safe.api.AuditContext;
import io.github.wrqtec.content.safe.api.AuditRequest;
import io.github.wrqtec.content.safe.api.AuditResult;
import io.github.wrqtec.content.safe.api.enums.ContentType;
import io.github.wrqtec.content.safe.api.exception.ContentSafeException;
import io.github.wrqtec.content.safe.api.exception.UnsupportedContentException;
import io.github.wrqtec.content.safe.api.util.Asserts;
import io.github.wrqtec.content.safe.model.AuditCapabilities;
import io.github.wrqtec.content.safe.model.ProviderPlan;
import io.github.wrqtec.content.safe.spi.AuditOperation;
import lombok.extern.slf4j.Slf4j;

/**
 * 腾讯云文本同步审核（TMS TextModeration）。Content 需 Base64(UTF-8) 编码，
 * 原文字符数上限见 {@link TencentLimits#maxTextChars()}，超限在调用前拒绝
 * （铁律 §5.2 能力先行）
 *
 * @author L.David
 */
@Slf4j
public class TencentTextModerationOperation implements AuditOperation {

    private final ProviderPlan plan;
    private final AuditCapabilities capabilities;
    private final TencentClientInvoker invoker;
    private final TencentAuditResultNormalizer normalizer;

    public TencentTextModerationOperation(ProviderPlan plan, TencentClientInvoker invoker,
                                          TencentAuditResultNormalizer normalizer) {
        this.plan = Asserts.notNull(plan, "plan");
        this.invoker = Asserts.notNull(invoker, "invoker");
        this.normalizer = Asserts.notNull(normalizer, "normalizer");
        this.capabilities = AuditCapabilities.builder()
                .contentTypes(ContentType.TEXT)
                .sync(true)
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
            throw new UnsupportedContentException("tencent text moderation only supports text content, got "
                    + request.type() + "/" + request.content().type());
        }
        String content = request.content().value();
        if (content.length() > TencentLimits.maxTextChars()) {
            throw new UnsupportedContentException("tencent text moderation supports at most "
                    + TencentLimits.maxTextChars() + " chars, got " + content.length());
        }
        TextModerationRequest sdkRequest = new TextModerationRequest();
        sdkRequest.setContent(Base64.getEncoder().encodeToString(content.getBytes(StandardCharsets.UTF_8)));
        sdkRequest.setBizType(plan.service());
        if (request.dataId() != null) {
            sdkRequest.setDataId(request.dataId());
        }
        applyContext(request.context(), sdkRequest);
        long startNanos = System.nanoTime();
        try {
            TextModerationResponse response = invoker.textModeration(sdkRequest);
            log.debug("tencent text moderation done planKey={} requestId={}", plan.planKey(),
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

    /** AuditContext → User/Device 风控字段映射（均为可选，缺失不设置） */
    private void applyContext(AuditContext context, TextModerationRequest sdkRequest) {
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
            device.setIP(context.ip());
            hasDevice = true;
        }
        if (hasDevice) {
            sdkRequest.setDevice(device);
        }
    }
}
