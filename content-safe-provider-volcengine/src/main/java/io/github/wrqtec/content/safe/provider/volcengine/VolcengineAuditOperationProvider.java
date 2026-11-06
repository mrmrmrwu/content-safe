package io.github.wrqtec.content.safe.provider.volcengine;

import java.util.List;
import java.util.Map;

import io.github.wrqtec.content.safe.api.enums.ContentType;
import io.github.wrqtec.content.safe.api.exception.ConfigInvalidException;
import io.github.wrqtec.content.safe.api.util.Asserts;
import io.github.wrqtec.content.safe.model.OperationLimits;
import io.github.wrqtec.content.safe.model.ProviderPlan;
import io.github.wrqtec.content.safe.model.SceneDefinition;
import io.github.wrqtec.content.safe.model.enums.ProviderLifecycle;
import io.github.wrqtec.content.safe.provider.volcengine.internal.VolcengineAuditResultNormalizer;
import io.github.wrqtec.content.safe.provider.volcengine.internal.VolcengineClientInvoker;
import io.github.wrqtec.content.safe.provider.volcengine.internal.VolcengineImageRiskOperation;
import io.github.wrqtec.content.safe.provider.volcengine.internal.VolcengineLimits;
import io.github.wrqtec.content.safe.provider.volcengine.internal.VolcengineParameters;
import io.github.wrqtec.content.safe.provider.volcengine.internal.VolcengineTextRiskOperation;
import io.github.wrqtec.content.safe.spi.AuditOperation;
import io.github.wrqtec.content.safe.spi.AuditOperationProvider;
import io.github.wrqtec.content.safe.spi.AuditResultNormalizer;
import lombok.extern.slf4j.Slf4j;

/**
 * 火山引擎内容安全操作提供者：把场景（SceneDefinition）按配置映射为
 * TextRisk / ImageContentRiskV2 操作。未映射的场景返回空列表由路由跳过
 * （铁律 §5.3 场景解耦，禁止硬编码场景 ID/Service 标识）
 *
 * @author L.David
 */
@Slf4j
public class VolcengineAuditOperationProvider implements AuditOperationProvider {

    private final VolcengineProviderProperties properties;
    private final VolcengineClientInvoker invoker;
    private final VolcengineAuditResultNormalizer normalizer;
    private final Integer appId;

    public VolcengineAuditOperationProvider(VolcengineProviderProperties properties,
                                            VolcengineClientInvoker invoker,
                                            VolcengineAuditResultNormalizer normalizer) {
        this.properties = Asserts.notNull(properties, "properties");
        this.invoker = Asserts.notNull(invoker, "invoker");
        this.normalizer = Asserts.notNull(normalizer, "normalizer");
        this.appId = parseAppId(properties.getExtra());
    }

    @Override
    public String provider() {
        return VolcengineProviderConstants.PROVIDER;
    }

    @Override
    public List<ContentType> supportedTypes() {
        return List.of(ContentType.TEXT, ContentType.IMAGE);
    }

    @Override
    public List<AuditOperation> operations(SceneDefinition scene) {
        Asserts.notNull(scene, "scene");
        Map<String, String> scenes = properties.getScenes();
        String service = scenes.get(scene.code());
        if (service == null || service.isBlank()) {
            log.debug("volcengine provider skip scene={} type={} (no Service mapping)", scene.code(), scene.type());
            return List.of();
        }
        return switch (scene.type()) {
            case TEXT -> List.of(new VolcengineTextRiskOperation(
                    syncPlan(VolcengineProviderConstants.OPERATION_TEXT_RISK,
                            service, VolcengineProviderConstants.API_VERSION_TEXT_RISK,
                            VolcengineLimits.textRisk()),
                    appId, scene,
                    requireContentKey(VolcengineProviderConstants.EXTRA_TEXT_PARAMETER_KEY, scene),
                    invoker, normalizer));
            case IMAGE -> List.of(new VolcengineImageRiskOperation(
                    syncPlan(VolcengineProviderConstants.OPERATION_IMAGE_CONTENT_RISK_V2,
                            service, VolcengineProviderConstants.API_VERSION_IMAGE_RISK,
                            VolcengineLimits.imageContentRisk()),
                    appId, scene,
                    requireContentKey(VolcengineProviderConstants.EXTRA_IMAGE_PARAMETER_KEY, scene),
                    invoker, normalizer));
            default -> {
                log.debug("volcengine provider skip scene={} unsupported type={}", scene.code(), scene.type());
                yield List.of();
            }
        };
    }

    /** 内容键名缺省即装配期 fail-fast（错误经 FailureAnalyzer 输出可读报告） */
    private String requireContentKey(String extraKey, SceneDefinition scene) {
        String value = VolcengineParameters.contentKey(properties.getExtra(), extraKey);
        if (value == null || value.isBlank()) {
            throw new ConfigInvalidException("volcengine scene '" + scene.code()
                    + "' requires extra '" + extraKey + "' (the content key defined by your "
                    + "Content Security service documentation)");
        }
        return value;
    }

    /** extra.app-id 解析为整型（装配期 fail-fast） */
    private static Integer parseAppId(Map<String, String> extra) {
        String value = extra.get(VolcengineProviderConstants.EXTRA_APP_ID);
        if (value == null || value.isBlank()) {
            throw new ConfigInvalidException("volcengine provider requires extra '"
                    + VolcengineProviderConstants.EXTRA_APP_ID + "' (Content Security console app id)");
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            throw new ConfigInvalidException("volcengine extra '"
                    + VolcengineProviderConstants.EXTRA_APP_ID + "' must be an integer, got '" + value + "'");
        }
    }

    /**
     * 构造同步操作计划（也供测试复用）
     */
    public static ProviderPlan syncPlan(String operation, String service, String apiVersion,
                                        OperationLimits limits) {
        return new ProviderPlan(
                VolcengineProviderConstants.PROVIDER,
                VolcengineProviderConstants.PRODUCT,
                apiVersion,
                operation,
                service,
                null, limits, ProviderLifecycle.ACTIVE);
    }

    /**
     * @return 归一化器（供测试复用）
     */
    public AuditResultNormalizer normalizer() {
        return normalizer;
    }
}
