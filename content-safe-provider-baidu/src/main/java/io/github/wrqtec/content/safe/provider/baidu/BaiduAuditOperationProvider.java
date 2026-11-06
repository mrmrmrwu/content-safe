package io.github.wrqtec.content.safe.provider.baidu;

import java.time.Duration;
import java.util.List;

import io.github.wrqtec.content.safe.api.enums.ContentType;
import io.github.wrqtec.content.safe.model.enums.AuditMode;
import io.github.wrqtec.content.safe.api.util.Asserts;
import io.github.wrqtec.content.safe.model.OperationLimits;
import io.github.wrqtec.content.safe.model.ProviderPlan;
import io.github.wrqtec.content.safe.model.SceneDefinition;
import io.github.wrqtec.content.safe.provider.baidu.internal.BaiduAuditResultNormalizer;
import io.github.wrqtec.content.safe.provider.baidu.internal.BaiduCensorClient;
import io.github.wrqtec.content.safe.provider.baidu.internal.BaiduImageCensorOperation;
import io.github.wrqtec.content.safe.provider.baidu.internal.BaiduLimits;
import io.github.wrqtec.content.safe.provider.baidu.internal.BaiduTextCensorOperation;
import io.github.wrqtec.content.safe.spi.AuditOperation;
import io.github.wrqtec.content.safe.spi.AuditOperationProvider;

/**
 * 百度内容审核 {@link AuditOperationProvider}
 *
 * <p>按场景配置注册同步操作：scenes 映射值 = 策略中心 strategyId（空字符串走默认策略）；
 * 未映射场景返回空列表，由路由跳过。
 *
 * @author L.David
 */
public class BaiduAuditOperationProvider implements AuditOperationProvider {

    private final BaiduProviderProperties properties;
    private final BaiduCensorClient client;
    private final BaiduAuditResultNormalizer normalizer;

    public BaiduAuditOperationProvider(BaiduProviderProperties properties, BaiduCensorClient client) {
        this.properties = Asserts.notNull(properties, "properties must not be null");
        this.client = Asserts.notNull(client, "client must not be null");
        this.normalizer = new BaiduAuditResultNormalizer();
    }

    @Override
    public String provider() {
        return BaiduProviderConstants.PROVIDER;
    }

    @Override
    public List<ContentType> supportedTypes() {
        return List.of(ContentType.TEXT, ContentType.IMAGE);
    }

    @Override
    public List<AuditOperation> operations(SceneDefinition scene) {
        Asserts.notNull(scene, "scene must not be null");
        String strategyId = properties.getScenes().get(scene.code());
        if (strategyId == null) {
            return List.of();
        }
        if (scene.type() == ContentType.TEXT) {
            return List.of(new BaiduTextCensorOperation(
                    syncPlan(BaiduProviderConstants.OPERATION_TEXT_CENSOR, strategyId, BaiduLimits.textSync()),
                    client, normalizer));
        }
        if (scene.type() == ContentType.IMAGE) {
            return List.of(new BaiduImageCensorOperation(
                    syncPlan(BaiduProviderConstants.OPERATION_IMAGE_CENSOR, strategyId, BaiduLimits.imageSync()),
                    client, normalizer));
        }
        return List.of();
    }

    /**
     * 同步操作计划（测试亦可直接复用）
     */
    public static ProviderPlan syncPlan(String operation, String strategyId, OperationLimits limits) {
        return new ProviderPlan(BaiduProviderConstants.PROVIDER, BaiduProviderConstants.PRODUCT,
                BaiduProviderConstants.API_VERSION, operation, strategyId, AuditMode.SYNC, limits, null);
    }
}
