package io.github.wrqtec.content.safe.provider.aliyun.internal;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

import com.aliyun.green20220302.models.TextModerationResponseBody;
import io.github.wrqtec.content.safe.api.AuditRequest;
import io.github.wrqtec.content.safe.api.AuditResult;
import io.github.wrqtec.content.safe.api.enums.ContentType;
import io.github.wrqtec.content.safe.api.exception.ContentSafeException;
import io.github.wrqtec.content.safe.api.exception.UnsupportedContentException;
import io.github.wrqtec.content.safe.api.util.Asserts;
import io.github.wrqtec.content.safe.model.AuditCapabilities;
import io.github.wrqtec.content.safe.model.ProviderPlan;
import io.github.wrqtec.content.safe.provider.aliyun.AliyunProviderConstants;
import io.github.wrqtec.content.safe.spi.AuditOperation;
import lombok.extern.slf4j.Slf4j;

/**
 * 阿里云文本同步审核（TextModeration）。仅接受 {@code ContentRefType.TEXT}；
 * 调用前做能力校验与限制校验（铁律 §5.2 能力先行）
 *
 * @author L.David
 */
@Slf4j
public class AliyunTextModerationOperation implements AuditOperation {

    /** serviceParameters 键 */
    static final String PARAM_CONTENT = "content";
    static final String PARAM_DATA_ID = "dataId";

    private final ProviderPlan plan;
    private final AuditCapabilities capabilities;
    private final GreenClientInvoker invoker;
    private final AliyunAuditResultNormalizer normalizer;

    public AliyunTextModerationOperation(ProviderPlan plan, GreenClientInvoker invoker,
                                         AliyunAuditResultNormalizer normalizer) {
        this.plan = Asserts.notNull(plan, "plan");
        this.invoker = Asserts.notNull(invoker, "invoker");
        this.normalizer = Asserts.notNull(normalizer, "normalizer");
        this.capabilities = AuditCapabilities.builder()
                .contentTypes(ContentType.TEXT)
                .sync(true)
                .aigcDetect(AliyunProviderConstants.isAigcService(plan.service()))
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
            throw new UnsupportedContentException("aliyun text moderation does not support content type "
                    + request.type() + "/" + request.content().type());
        }
        String content = request.content().value();
        long contentBytes = content == null ? 0 : content.getBytes(StandardCharsets.UTF_8).length;
        long maxBytes = plan.limits().maxContentBytes();
        if (maxBytes > 0 && contentBytes > maxBytes) {
            throw new UnsupportedContentException("content size " + contentBytes
                    + " exceeds aliyun text moderation limit " + maxBytes);
        }
        Map<String, String> serviceParameters = new LinkedHashMap<>();
        serviceParameters.put(PARAM_CONTENT, content);
        if (request.dataId() != null) {
            serviceParameters.put(PARAM_DATA_ID, request.dataId());
        }
        long startNanos = System.nanoTime();
        try {
            TextModerationResponseBody body = invoker.textModeration(plan.service(), serviceParameters);
            AliyunErrorMapper.assertSuccess(body.getCode(), body.getMessage());
            log.debug("aliyun text moderation done planKey={} requestId={}", plan.planKey(), body.getRequestId());
            Duration cost = Duration.ofNanos(System.nanoTime() - startNanos);
            return normalizer.normalize(body,
                    AliyunAuditResultNormalizer.context(plan, request.dataId(), cost));
        } catch (ContentSafeException e) {
            throw e;
        } catch (Exception e) {
            throw AliyunErrorMapper.fromException(e);
        }
    }
}
