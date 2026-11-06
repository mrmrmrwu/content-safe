package io.github.wrqtec.content.safe.provider.shumei;

import java.util.List;

import io.github.wrqtec.content.safe.api.enums.ContentType;
import io.github.wrqtec.content.safe.model.enums.AuditMode;
import io.github.wrqtec.content.safe.api.util.Asserts;
import io.github.wrqtec.content.safe.model.OperationLimits;
import io.github.wrqtec.content.safe.model.ProviderPlan;
import io.github.wrqtec.content.safe.model.SceneDefinition;
import io.github.wrqtec.content.safe.provider.shumei.internal.ShumeiAuditResultNormalizer;
import io.github.wrqtec.content.safe.provider.shumei.internal.ShumeiClientInvoker;
import io.github.wrqtec.content.safe.provider.shumei.internal.ShumeiImageDetectOperation;
import io.github.wrqtec.content.safe.provider.shumei.internal.ShumeiLimits;
import io.github.wrqtec.content.safe.provider.shumei.internal.ShumeiTextDetectOperation;
import io.github.wrqtec.content.safe.spi.AuditOperation;
import io.github.wrqtec.content.safe.spi.AuditOperationProvider;

/**
 * 数美内容安全 {@link AuditOperationProvider}
 *
 * <p>按场景配置注册同步操作：scenes 映射值 = 与数美约定的 eventId（官方强约定）；
 * 未映射场景返回空列表，由路由跳过。检测类型经 extra 的 text-type/image-type 配置，
 * 缺省 TEXTRISK 与 POLITY_EROTIC_VIOLENT_QRCODE_ADVERT_IMGTEXTRISK。
 *
 * @author L.David
 */
public class ShumeiAuditOperationProvider implements AuditOperationProvider {

    private final ShumeiProviderProperties properties;
    private final ShumeiClientInvoker client;
    private final ShumeiAuditResultNormalizer normalizer;

    public ShumeiAuditOperationProvider(ShumeiProviderProperties properties, ShumeiClientInvoker client) {
        this.properties = Asserts.notNull(properties, "properties");
        this.client = Asserts.notNull(client, "client");
        this.normalizer = new ShumeiAuditResultNormalizer();
    }

    @Override
    public String provider() {
        return ShumeiProviderConstants.PROVIDER;
    }

    @Override
    public List<ContentType> supportedTypes() {
        return List.of(ContentType.TEXT, ContentType.IMAGE);
    }

    @Override
    public List<AuditOperation> operations(SceneDefinition scene) {
        Asserts.notNull(scene, "scene");
        String eventId = properties.getScenes().get(scene.code());
        if (eventId == null) {
            return List.of();
        }
        if (scene.type() == ContentType.TEXT) {
            return List.of(new ShumeiTextDetectOperation(
                    syncPlan(ShumeiProviderConstants.OPERATION_TEXT_DETECT, eventId, ShumeiLimits.textSync()),
                    client, normalizer, textTypeOf(), scene));
        }
        if (scene.type() == ContentType.IMAGE) {
            return List.of(new ShumeiImageDetectOperation(
                    syncPlan(ShumeiProviderConstants.OPERATION_IMAGE_DETECT, eventId, ShumeiLimits.imageSync()),
                    client, normalizer, imageTypeOf(), scene));
        }
        return List.of();
    }

    /**
     * 同步操作计划（测试亦可直接复用）
     */
    public static ProviderPlan syncPlan(String operation, String eventId, OperationLimits limits) {
        return new ProviderPlan(ShumeiProviderConstants.PROVIDER, ShumeiProviderConstants.PRODUCT,
                ShumeiProviderConstants.API_VERSION, operation, eventId, AuditMode.SYNC, limits, null);
    }

    private String textTypeOf() {
        String type = properties.extraValue(ShumeiProviderConstants.EXTRA_TEXT_TYPE);
        return type == null ? ShumeiProviderConstants.DEFAULT_TEXT_TYPE : type;
    }

    private String imageTypeOf() {
        String type = properties.extraValue(ShumeiProviderConstants.EXTRA_IMAGE_TYPE);
        return type == null ? ShumeiProviderConstants.DEFAULT_IMAGE_TYPE : type;
    }
}
