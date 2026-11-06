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
 * 数美图片同步检测操作（image/v4）
 *
 * <p>img 支持 URL / base64 / bytes（自动编码 base64）三通道；
 * 动图截帧参数（interval/maxFrame）可经场景 ext 传入。
 *
 * @author L.David
 */
@Slf4j
public class ShumeiImageDetectOperation implements AuditOperation {

    private final ProviderPlan plan;
    private final ShumeiClientInvoker client;
    private final ShumeiAuditResultNormalizer normalizer;
    private final String type;
    private final SceneDefinition scene;
    private final AuditCapabilities capabilities;

    public ShumeiImageDetectOperation(ProviderPlan plan, ShumeiClientInvoker client,
                                      ShumeiAuditResultNormalizer normalizer, String type,
                                      SceneDefinition scene) {
        this.plan = Asserts.notNull(plan, "plan");
        this.client = Asserts.notNull(client, "client");
        this.normalizer = Asserts.notNull(normalizer, "normalizer");
        this.type = Asserts.notBlank(type, "type");
        this.scene = scene;
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
            throw new UnsupportedContentException("shumei image detect only supports IMAGE content");
        }
        String tokenId = ShumeiPayloads.tokenIdOf(request);
        long startNanos = System.nanoTime();
        try {
            ShumeiSyncResponse response = client.imageDetect(plan.service(), type,
                    ShumeiPayloads.imageData(request.content(), tokenId, request.dataId(),
                            ShumeiTextDetectOperation.ipOf(request), scene));
            log.debug("shumei image detect done, planKey={}, requestId={}",
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
}
