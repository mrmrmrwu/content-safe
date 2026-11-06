package io.github.wrqtec.content.safe.provider.volcengine.internal;

import java.time.Duration;

import com.volcengine.model.request.RiskDetectionRequest;
import com.volcengine.model.response.TextRiskResponse;
import io.github.wrqtec.content.safe.api.AuditRequest;
import io.github.wrqtec.content.safe.api.AuditResult;
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
 * 火山引擎文本同步检测（TextRisk）。文本原文写入 extra 配置的 parameters
 * 内容键（铁律 §5.2 能力先行，非 TEXT 内容调用前拒绝）
 *
 * @author L.David
 */
@Slf4j
public class VolcengineTextRiskOperation implements AuditOperation {

    private final ProviderPlan plan;
    private final Integer appId;
    private final SceneDefinition scene;
    private final String contentKey;
    private final AuditCapabilities capabilities;
    private final VolcengineClientInvoker invoker;
    private final VolcengineAuditResultNormalizer normalizer;

    public VolcengineTextRiskOperation(ProviderPlan plan, Integer appId, SceneDefinition scene,
                                       String contentKey, VolcengineClientInvoker invoker,
                                       VolcengineAuditResultNormalizer normalizer) {
        this.plan = Asserts.notNull(plan, "plan");
        this.appId = Asserts.notNull(appId, "appId");
        this.scene = scene;
        this.contentKey = Asserts.notBlank(contentKey, "contentKey");
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
            throw new UnsupportedContentException("volcengine text risk only supports text content, got "
                    + request.type() + "/" + request.content().type());
        }
        String content = request.content().value();
        RiskDetectionRequest sdkRequest = VolcengineParameters.envelope(
                appId, plan.service(), contentKey, content, scene);
        long startNanos = System.nanoTime();
        try {
            TextRiskResponse response = invoker.textRisk(sdkRequest);
            Duration cost = Duration.ofNanos(System.nanoTime() - startNanos);
            log.debug("volcengine text risk done planKey={} cost={}ms", plan.planKey(), cost.toMillis());
            return normalizer.normalize(response,
                    VolcengineAuditResultNormalizer.context(plan, request.dataId(), cost));
        } catch (ContentSafeException e) {
            throw e;
        } catch (Exception e) {
            throw VolcengineErrorMapper.fromException(e);
        }
    }
}
