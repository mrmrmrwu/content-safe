package io.github.wrqtec.content.safe.provider.tencent;

import java.util.List;
import java.util.Map;

import io.github.wrqtec.content.safe.api.enums.ContentType;
import io.github.wrqtec.content.safe.model.OperationLimits;
import io.github.wrqtec.content.safe.model.enums.AuditMode;
import io.github.wrqtec.content.safe.model.enums.ProviderLifecycle;
import io.github.wrqtec.content.safe.model.ProviderPlan;
import io.github.wrqtec.content.safe.model.SceneDefinition;
import io.github.wrqtec.content.safe.spi.AuditOperation;
import io.github.wrqtec.content.safe.spi.AuditOperationProvider;
import io.github.wrqtec.content.safe.spi.AuditResultNormalizer;
import io.github.wrqtec.content.safe.api.exception.ConfigInvalidException;
import io.github.wrqtec.content.safe.api.util.Asserts;
import io.github.wrqtec.content.safe.provider.tencent.internal.TencentAuditResultNormalizer;
import io.github.wrqtec.content.safe.provider.tencent.internal.TencentClientInvoker;
import io.github.wrqtec.content.safe.provider.tencent.internal.TencentImageAsyncOperation;
import io.github.wrqtec.content.safe.provider.tencent.internal.TencentImageModerationOperation;
import io.github.wrqtec.content.safe.provider.tencent.internal.TencentLimits;
import io.github.wrqtec.content.safe.provider.tencent.internal.TencentTextModerationOperation;
import lombok.extern.slf4j.Slf4j;

/**
 * 腾讯云内容安全操作提供者：把场景（SceneDefinition）按配置映射为
 * 对应的 TMS TextModeration / IMS ImageModeration 操作。未映射的场景
 * 返回空列表由路由跳过（铁律 §5.3 场景解耦，禁止硬编码场景 ID）
 *
 * @author L.David
 */
@Slf4j
public class TencentAuditOperationProvider implements AuditOperationProvider {

    private final TencentProviderProperties properties;
    private final TencentClientInvoker invoker;
    private final TencentAuditResultNormalizer normalizer;

    public TencentAuditOperationProvider(TencentProviderProperties properties,
                                         TencentClientInvoker invoker,
                                         TencentAuditResultNormalizer normalizer) {
        this.properties = Asserts.notNull(properties, "properties");
        this.invoker = Asserts.notNull(invoker, "invoker");
        this.normalizer = Asserts.notNull(normalizer, "normalizer");
    }

    @Override
    public String provider() {
        return TencentProviderConstants.PROVIDER;
    }

    @Override
    public List<ContentType> supportedTypes() {
        return List.of(ContentType.TEXT, ContentType.IMAGE);
    }

    @Override
    public List<AuditOperation> operations(SceneDefinition scene) {
        Asserts.notNull(scene, "scene");
        Map<String, String> scenes = properties.getScenes();
        String bizType = scenes.get(scene.code());
        if (bizType != null && !bizType.isBlank()) {
            return buildOperation(scene, bizType, null);
        }
        Map<String, String> aigcScenes = properties.getAigcScenes();
        String aigcBizType = aigcScenes.get(scene.code());
        if (aigcBizType != null && !aigcBizType.isBlank()
                && (ContentType.TEXT == scene.type() || ContentType.IMAGE == scene.type())) {
            return buildOperation(scene, aigcBizType,
                    ContentType.TEXT == scene.type()
                            ? TencentProviderConstants.TEXT_TYPE_AIGC
                            : TencentProviderConstants.IMAGE_TYPE_AIGC);
        }
        log.debug("tencent provider skip scene={} type={} (no BizType mapping)", scene.code(), scene.type());
        return List.of();
    }

    private List<AuditOperation> buildOperation(SceneDefinition scene, String bizType, String type) {
        switch (scene.type()) {
            case TEXT -> {
                ProviderPlan plan = syncPlan(TencentProviderConstants.OPERATION_TEXT_MODERATION,
                        bizType, TencentLimits.textSync());
                return List.of(new TencentTextModerationOperation(plan, type, invoker, normalizer));
            }
            case IMAGE -> {
                ProviderPlan plan = syncPlan(TencentProviderConstants.OPERATION_IMAGE_MODERATION,
                        bizType, TencentLimits.imageSync());
                Integer gifInterval = parsePositiveInt(properties.getExtra(), "gifInterval");
                Integer gifMaxFrames = parsePositiveInt(properties.getExtra(), "gifMaxFrames");
                String callbackUrl = properties.getExtra().get(TencentProviderConstants.EXTRA_CALLBACK_URL);
                ProviderPlan asyncPlan = asyncPlan(bizType);
                return List.of(
                        new TencentImageModerationOperation(plan, type, gifInterval, gifMaxFrames,
                                invoker, normalizer),
                        new TencentImageAsyncOperation(asyncPlan, gifInterval, gifMaxFrames, callbackUrl,
                                invoker, normalizer));
            }
            default -> {
                log.debug("tencent provider skip scene={} unsupported type={}", scene.code(), scene.type());
                return List.of();
            }
        }
    }

    /** extra 配置解析正整数（缺失/非法时返回 null 并告警，不中断装配） */
    private static Integer parsePositiveInt(Map<String, String> extra, String key) {
        String value = extra.get(key);
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            int parsed = Integer.parseInt(value.trim());
            if (parsed <= 0) {
                log.warn("tencent extra '{}' must be positive, got {}; ignored", key, value);
                return null;
            }
            return parsed;
        } catch (NumberFormatException e) {
            log.warn("tencent extra '{}' must be an integer, got '{}'; ignored", key, value);
            return null;
        }
    }

    /**
     * 构造同步操作计划（也供测试复用）
     */
    public static ProviderPlan syncPlan(String operation, String service, OperationLimits limits) {
        return new ProviderPlan(
                TencentProviderConstants.PROVIDER,
                TencentProviderConstants.PRODUCT,
                TencentProviderConstants.API_VERSION,
                operation,
                service,
                null, limits, ProviderLifecycle.ACTIVE);
    }

    /** 图片异步任务创建操作计划（仅回调推送，无主动查询） */
    public static ProviderPlan asyncPlan(String service) {
        return new ProviderPlan(
                TencentProviderConstants.PROVIDER,
                TencentProviderConstants.PRODUCT,
                TencentProviderConstants.API_VERSION,
                TencentProviderConstants.OPERATION_IMAGE_ASYNC_CREATE,
                service,
                AuditMode.ASYNC_CALLBACK, TencentLimits.imageAsync(), ProviderLifecycle.ACTIVE);
    }
}
