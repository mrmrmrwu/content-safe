package io.github.wrqtec.content.safe.provider.baidu.internal;

import java.time.Duration;
import java.util.Base64;

import io.github.wrqtec.content.safe.api.AuditRequest;
import io.github.wrqtec.content.safe.api.AuditResult;
import io.github.wrqtec.content.safe.api.ContentRef;
import io.github.wrqtec.content.safe.api.enums.ContentRefType;
import io.github.wrqtec.content.safe.api.enums.ContentType;
import io.github.wrqtec.content.safe.api.exception.ContentSafeException;
import io.github.wrqtec.content.safe.api.exception.UnsupportedContentException;
import io.github.wrqtec.content.safe.api.util.Asserts;
import io.github.wrqtec.content.safe.model.AuditCapabilities;
import io.github.wrqtec.content.safe.model.ProviderPlan;
import io.github.wrqtec.content.safe.spi.AuditOperation;
import lombok.extern.slf4j.Slf4j;

/**
 * 百度图像同步审核操作
 *
 * <p>支持三种输入形态：公网 URL（imgUrl 参数）、base64 字符串、原始字节（内部转 base64）。
 * 图片大小上限按 base64 后 4MB 计算。
 *
 * @author L.David
 */
@Slf4j
public class BaiduImageCensorOperation implements AuditOperation {

    /** base64 编码体积膨胀率（4/3），用于 bytes 形态前置校验 */
    private static final double BASE64_EXPANSION = 4D / 3D;

    private final ProviderPlan plan;
    private final BaiduCensorClient client;
    private final BaiduAuditResultNormalizer normalizer;
    private final AuditCapabilities capabilities;

    public BaiduImageCensorOperation(ProviderPlan plan, BaiduCensorClient client,
                                     BaiduAuditResultNormalizer normalizer) {
        this.plan = Asserts.notNull(plan, "plan");
        this.client = Asserts.notNull(client, "client");
        this.normalizer = Asserts.notNull(normalizer, "normalizer");
        this.capabilities = AuditCapabilities.builder()
                .contentTypes(ContentType.IMAGE)
                .sync(true)
                .url(true)
                .base64(true)
                .bytes(true)
                .maxBase64Size(BaiduLimits.maxImageBytes())
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
            throw new UnsupportedContentException(
                    "baidu image censor only supports IMAGE content via url/base64/bytes");
        }
        long startNanos = System.nanoTime();
        try {
            BaiduCensorResponse response = invoke(request.content());
            log.debug("baidu image censor done, planKey={}, logId={}", plan.planKey(), response.getLogId());
            return normalizer.normalize(response,
                    BaiduAuditResultNormalizer.context(plan, request.dataId(), Duration.ofNanos(
                            System.nanoTime() - startNanos)));
        } catch (ContentSafeException e) {
            throw e;
        } catch (Exception e) {
            throw BaiduErrorMapper.fromException(e);
        }
    }

    private BaiduCensorResponse invoke(ContentRef content) {
        ContentRefType type = content.type();
        if (type == ContentRefType.URL) {
            return client.imageCensorByUrl(content.value(), plan.service());
        }
        if (type == ContentRefType.BYTES) {
            byte[] bytes = content.bytes();
            if ((long) (bytes.length * BASE64_EXPANSION) > plan.limits().maxContentBytes()) {
                throw new UnsupportedContentException(
                        "baidu image censor content too large after base64: rawBytes=" + bytes.length);
            }
            return client.imageCensorByBase64(Base64.getEncoder().encodeToString(bytes), plan.service());
        }
        // BASE64 形态：value 即 base64 字符串
        String base64 = content.value();
        if (base64.length() > plan.limits().maxContentBytes()) {
            throw new UnsupportedContentException(
                    "baidu image censor content too large: " + base64.length() + " > "
                            + plan.limits().maxContentBytes());
        }
        return client.imageCensorByBase64(base64, plan.service());
    }
}
