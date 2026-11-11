package io.github.wrqtec.content.safe.provider.aliyun;

import java.util.ArrayList;
import java.util.List;

import io.github.wrqtec.content.safe.api.enums.ContentType;
import io.github.wrqtec.content.safe.api.util.Asserts;
import io.github.wrqtec.content.safe.model.OperationLimits;
import io.github.wrqtec.content.safe.model.ProviderPlan;
import io.github.wrqtec.content.safe.model.SceneDefinition;
import io.github.wrqtec.content.safe.model.enums.AuditMode;
import io.github.wrqtec.content.safe.spi.AuditOperation;
import io.github.wrqtec.content.safe.spi.AuditOperationProvider;
import io.github.wrqtec.content.safe.provider.aliyun.internal.AliyunAuditResultNormalizer;
import io.github.wrqtec.content.safe.provider.aliyun.internal.AliyunImageAsyncOperation;
import io.github.wrqtec.content.safe.provider.aliyun.internal.AliyunImageModerationOperation;
import io.github.wrqtec.content.safe.provider.aliyun.internal.AliyunLimits;
import io.github.wrqtec.content.safe.provider.aliyun.internal.AliyunTextModerationOperation;
import io.github.wrqtec.content.safe.provider.aliyun.internal.AliyunVideoAsyncOperation;
import io.github.wrqtec.content.safe.provider.aliyun.internal.GreenClientInvoker;

/**
 * 阿里云内容安全 provider：按场景配置生成同步 TextModeration / ImageModeration、
 * 异步 ImageAsyncModeration / VideoModeration 操作。
 * 数据驱动（铁律 §9.2）：文本/图片同步的云厂商 service 标识只来自配置
 * {@code content.safe.providers.aliyun.scenes}；视频文件审核的 Service 为官方
 * 固定值 videoDetection，scenes 配置值仅作场景开关。场景未映射则返回空操作列表，
 * 由路由跳过该供应商
 *
 * @author L.David
 */
public class AliyunAuditOperationProvider implements AuditOperationProvider {

    private final GreenClientInvoker invoker;
    private final AliyunProviderProperties properties;
    private final AliyunAuditResultNormalizer normalizer;

    public AliyunAuditOperationProvider(GreenClientInvoker invoker, AliyunProviderProperties properties) {
        this.invoker = Asserts.notNull(invoker, "invoker");
        this.properties = Asserts.notNull(properties, "properties");
        this.normalizer = new AliyunAuditResultNormalizer();
    }

    @Override
    public String provider() {
        return AliyunProviderConstants.PROVIDER;
    }

    @Override
    public List<ContentType> supportedTypes() {
        return List.of(ContentType.TEXT, ContentType.IMAGE, ContentType.VIDEO);
    }

    @Override
    public List<AuditOperation> operations(SceneDefinition scene) {
        Asserts.notNull(scene, "scene");
        String service = properties.getScenes().get(scene.code());
        if (service == null) {
            return List.of();
        }
        List<AuditOperation> operations = new ArrayList<>(2);
        if (ContentType.TEXT == scene.type()) {
            operations.add(new AliyunTextModerationOperation(
                    syncPlan(AliyunProviderConstants.OPERATION_TEXT_MODERATION, service, AliyunLimits.textSync()),
                    invoker, normalizer));
        } else if (ContentType.IMAGE == scene.type()) {
            operations.add(new AliyunImageModerationOperation(
                    syncPlan(AliyunProviderConstants.OPERATION_IMAGE_MODERATION, service, AliyunLimits.imageSync()),
                    invoker, normalizer));
            operations.add(new AliyunImageAsyncOperation(
                    asyncPlan(service), invoker, normalizer,
                    properties.getExtra().get(AliyunProviderConstants.EXTRA_CALLBACK_URL),
                    properties.getExtra().get(AliyunProviderConstants.EXTRA_CALLBACK_SEED)));
        } else if (ContentType.VIDEO == scene.type()) {
            operations.add(new AliyunVideoAsyncOperation(videoAsyncPlan(), invoker, normalizer));
        }
        return List.copyOf(operations);
    }

    private ProviderPlan syncPlan(String operation, String service, OperationLimits limits) {
        return new ProviderPlan(
                AliyunProviderConstants.PROVIDER,
                AliyunProviderConstants.PRODUCT,
                AliyunProviderConstants.API_VERSION,
                operation,
                service,
                AuditMode.SYNC,
                limits,
                null);
    }

    private ProviderPlan asyncPlan(String service) {
        return new ProviderPlan(
                AliyunProviderConstants.PROVIDER,
                AliyunProviderConstants.PRODUCT,
                AliyunProviderConstants.API_VERSION,
                AliyunProviderConstants.OPERATION_IMAGE_ASYNC_MODERATION,
                service,
                AuditMode.ASYNC_QUERY,
                AliyunLimits.imageAsync(),
                null);
    }

    /** 视频文件审核 Service 为官方固定值 videoDetection（scenes 配置值仅作场景开关） */
    private ProviderPlan videoAsyncPlan() {
        return new ProviderPlan(
                AliyunProviderConstants.PROVIDER,
                AliyunProviderConstants.PRODUCT,
                AliyunProviderConstants.API_VERSION,
                AliyunProviderConstants.OPERATION_VIDEO_MODERATION,
                AliyunProviderConstants.SERVICE_VIDEO_DETECTION,
                AuditMode.ASYNC_QUERY,
                AliyunLimits.videoModeration(),
                null);
    }
}
