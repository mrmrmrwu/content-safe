package io.github.wrqtec.content.safe.provider.shumei.internal;

import java.time.Duration;

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
 * 数美文本同步检测操作（text/v4）
 *
 * @author L.David
 */
@Slf4j
public class ShumeiTextDetectOperation implements AuditOperation {

    private final ProviderPlan plan;
    private final ShumeiClientInvoker client;
    private final ShumeiAuditResultNormalizer normalizer;
    private final String type;
    private final SceneDefinition scene;
    private final AuditCapabilities capabilities;

    public ShumeiTextDetectOperation(ProviderPlan plan, ShumeiClientInvoker client,
                                     ShumeiAuditResultNormalizer normalizer, String type,
                                     SceneDefinition scene) {
        this.plan = Asserts.notNull(plan, "plan must not be null");
        this.client = Asserts.notNull(client, "client must not be null");
        this.normalizer = Asserts.notNull(normalizer, "normalizer must not be null");
        this.type = Asserts.notBlank(type, "type must not be blank");
        this.scene = scene;
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
        Asserts.notNull(request, "request must not be null");
        if (!capabilities.supports(request)) {
            throw new UnsupportedContentException("shumei text detect only supports TEXT content");
        }
        String text = request.content().value();
        long chars = text.codePointCount(0, text.length());
        if (chars > ShumeiLimits.maxTextChars()) {
            throw new UnsupportedContentException(
                    "shumei text detect content too large: " + chars + " chars > "
                            + ShumeiLimits.maxTextChars());
        }
        String tokenId = ShumeiPayloads.tokenIdOf(request);
        long startNanos = System.nanoTime();
        try {
            ShumeiSyncResponse response = client.textDetect(plan.service(), type,
                    ShumeiPayloads.textData(text, tokenId, request.dataId(),
                            ipOf(request), scene));
            log.debug("shumei text detect done, planKey={}, requestId={}",
                    plan.planKey(), response.getRequestId());
            return normalizer.normalize(response,
                    ShumeiAuditResultNormalizer.context(plan, request.dataId(), Duration.ofNanos(
                            System.nanoTime() - startNanos)));
        } catch (ContentSafeException e) {
            throw e;
        } catch (Exception e) {
            throw ShumeiErrorMapper.fromException(e);
        }
    }

    static String ipOf(AuditRequest request) {
        return request.context() == null ? null : request.context().ip();
    }
}
