package io.github.wrqtec.content.safe.provider.aliyun.internal;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

import com.aliyun.green20220302.models.ImageModerationResponseBody;
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
 * 阿里云图片同步审核（ImageModeration）。2.0 版本仅接受公网可达 URL
 * （铁律 §5.2 能力先行；base64/bytes 直接拒绝）
 *
 * @author L.David
 */
@Slf4j
public class AliyunImageModerationOperation implements AuditOperation {

    /** serviceParameters 键 */
    static final String PARAM_IMAGE_URL = "imageUrl";
    static final String PARAM_DATA_ID = "dataId";

    private final ProviderPlan plan;
    private final AuditCapabilities capabilities;
    private final GreenClientInvoker invoker;
    private final AliyunAuditResultNormalizer normalizer;

    public AliyunImageModerationOperation(ProviderPlan plan, GreenClientInvoker invoker,
                                          AliyunAuditResultNormalizer normalizer) {
        this.plan = Asserts.notNull(plan, "plan");
        this.invoker = Asserts.notNull(invoker, "invoker");
        this.normalizer = Asserts.notNull(normalizer, "normalizer");
        this.capabilities = AuditCapabilities.builder()
                .contentTypes(ContentType.IMAGE)
                .sync(true)
                .url(true)
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
            throw new UnsupportedContentException("aliyun image moderation only supports public url, got content type "
                    + request.type() + "/" + request.content().type());
        }
        Map<String, String> serviceParameters = new LinkedHashMap<>();
        serviceParameters.put(PARAM_IMAGE_URL, request.content().value());
        if (request.dataId() != null) {
            serviceParameters.put(PARAM_DATA_ID, request.dataId());
        }
        long startNanos = System.nanoTime();
        try {
            ImageModerationResponseBody body = invoker.imageModeration(plan.service(), serviceParameters);
            AliyunErrorMapper.assertSuccess(body.getCode(), body.getMsg());
            log.debug("aliyun image moderation done planKey={} requestId={}", plan.planKey(), body.getRequestId());
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
