package io.github.wrqtec.content.safe.provider.baidu.internal;

import java.nio.charset.StandardCharsets;
import java.time.Duration;

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
 * 百度文本同步审核操作
 *
 * @author L.David
 */
@Slf4j
public class BaiduTextCensorOperation implements AuditOperation {

    private final ProviderPlan plan;
    private final BaiduCensorClient client;
    private final BaiduAuditResultNormalizer normalizer;
    private final AuditCapabilities capabilities;

    public BaiduTextCensorOperation(ProviderPlan plan, BaiduCensorClient client,
                                    BaiduAuditResultNormalizer normalizer) {
        this.plan = Asserts.notNull(plan, "plan");
        this.client = Asserts.notNull(client, "client");
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
            throw new UnsupportedContentException("baidu text censor only supports TEXT content");
        }
        String text = request.content().value();
        long bytes = text.getBytes(StandardCharsets.UTF_8).length;
        if (bytes > plan.limits().maxContentBytes()) {
            throw new UnsupportedContentException(
                    "baidu text censor content too large: " + bytes + " > " + plan.limits().maxContentBytes());
        }
        long startNanos = System.nanoTime();
        try {
            BaiduCensorResponse response = client.textCensor(text, plan.service());
            log.debug("baidu text censor done, planKey={}, logId={}", plan.planKey(), response.getLogId());
            return normalizer.normalize(response,
                    BaiduAuditResultNormalizer.context(plan, request.dataId(), Duration.ofNanos(
                            System.nanoTime() - startNanos)));
        } catch (ContentSafeException e) {
            throw e;
        } catch (Exception e) {
            throw BaiduErrorMapper.fromException(e);
        }
    }
}
