package io.github.wrqtec.content.safe.provider.volcengine.internal;

import java.time.Duration;
import java.util.Base64;

import com.volcengine.model.request.RiskDetectionRequest;
import com.volcengine.model.response.ImageRiskDetectionResponse;
import io.github.wrqtec.content.safe.api.AuditRequest;
import io.github.wrqtec.content.safe.api.AuditResult;
import io.github.wrqtec.content.safe.api.enums.ContentRefType;
import io.github.wrqtec.content.safe.api.enums.ContentType;
import io.github.wrqtec.content.safe.api.exception.ContentSafeException;
import io.github.wrqtec.content.safe.api.exception.UnsupportedContentException;
import io.github.wrqtec.content.safe.api.util.Asserts;
import io.github.wrqtec.content.safe.model.AuditCapabilities;
import io.github.wrqtec.content.safe.model.ProviderPlan;
import io.github.wrqtec.content.safe.model.SceneDefinition;
import io.github.wrqtec.content.safe.spi.AuditOperation;
import lombok.extern.slf4j.Slf4j;

/**
 * 火山引擎图片同步检测（ImageContentRiskV2）。URL 原文 / base64 值 / bytes
 * （内部编码）写入 extra 配置的 parameters 内容键；具体取值形态由所配
 * Service 的控制台文档定义（铁律 §5.2 能力先行）
 *
 * @author L.David
 */
@Slf4j
public class VolcengineImageRiskOperation implements AuditOperation {

    private final ProviderPlan plan;
    private final Integer appId;
    private final SceneDefinition scene;
    private final String contentKey;
    private final AuditCapabilities capabilities;
    private final VolcengineClientInvoker invoker;
    private final VolcengineAuditResultNormalizer normalizer;

    public VolcengineImageRiskOperation(ProviderPlan plan, Integer appId, SceneDefinition scene,
                                        String contentKey, VolcengineClientInvoker invoker,
                                        VolcengineAuditResultNormalizer normalizer) {
        this.plan = Asserts.notNull(plan, "plan");
        this.appId = Asserts.notNull(appId, "appId");
        this.scene = scene;
        this.contentKey = Asserts.notBlank(contentKey, "contentKey");
        this.invoker = Asserts.notNull(invoker, "invoker");
        this.normalizer = Asserts.notNull(normalizer, "normalizer");
        this.capabilities = AuditCapabilities.builder()
                .contentTypes(ContentType.IMAGE)
                .sync(true)
                .url(true)
                .base64(true)
                .bytes(true)
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
            throw new UnsupportedContentException("volcengine image content risk supports url/base64/bytes, got "
                    + request.type() + "/" + request.content().type());
        }
        String content = contentOf(request);
        RiskDetectionRequest sdkRequest = VolcengineParameters.envelope(
                appId, plan.service(), contentKey, content, scene);
        long startNanos = System.nanoTime();
        try {
            ImageRiskDetectionResponse response = invoker.imageContentRiskV2(sdkRequest);
            Duration cost = Duration.ofNanos(System.nanoTime() - startNanos);
            log.debug("volcengine image content risk done planKey={} cost={}ms", plan.planKey(), cost.toMillis());
            return normalizer.normalize(response,
                    VolcengineAuditResultNormalizer.context(plan, request.dataId(), cost));
        } catch (ContentSafeException e) {
            throw e;
        } catch (Exception e) {
            throw VolcengineErrorMapper.fromException(e);
        }
    }

    /** 内容装配：URL/BASE64 用引用原值；BYTES 内部转 base64 */
    private String contentOf(AuditRequest request) {
        ContentRefType contentType = request.content().type();
        return switch (contentType) {
            case URL, BASE64, OBJECT_KEY -> request.content().value();
            case BYTES -> Base64.getEncoder().encodeToString(request.content().bytes());
            default -> throw new UnsupportedContentException(
                    "volcengine image content risk does not support content type " + contentType);
        };
    }
}
