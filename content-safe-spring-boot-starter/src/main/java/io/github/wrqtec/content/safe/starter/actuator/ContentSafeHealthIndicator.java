package io.github.wrqtec.content.safe.starter.actuator;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import io.github.wrqtec.content.safe.api.util.ErrorMessages;
import io.github.wrqtec.content.safe.core.SceneRegistry;
import io.github.wrqtec.content.safe.core.degrade.CircuitBreaker;
import io.github.wrqtec.content.safe.core.degrade.CircuitBreakerRegistry;
import io.github.wrqtec.content.safe.core.limiter.RateLimiterRegistry;
import io.github.wrqtec.content.safe.model.SceneDefinition;
import io.github.wrqtec.content.safe.spi.AuditOperationProvider;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;

/**
 * ContentSafe 健康指示，仅汇总本地状态（场景数、供应商数、熔断器快照），
 * 不发起任何远程探测
 *
 * @author L.David
 */
public class ContentSafeHealthIndicator implements HealthIndicator {

    /** 存在 OPEN 熔断器时的自定义状态 */
    public static final String STATUS_DEGRADED = "DEGRADED";

    /** 健康详情 key：场景数 */
    private static final String DETAIL_SCENES = "scenes";
    /** 健康详情 key：供应商数 */
    private static final String DETAIL_PROVIDERS = "providers";
    /** 健康详情 key：处于 OPEN 状态的熔断器 key 列表 */
    private static final String DETAIL_OPEN_CIRCUIT_BREAKERS = "openCircuitBreakers";
    /** 健康详情 key：限流器快照（planKey → 限流器名称） */
    private static final String DETAIL_RATE_LIMITERS = "rateLimiters";
    /** 健康详情 key：场景 → 供应商映射表（场景无映射时值为空列表） */
    private static final String DETAIL_SCENE_MAPPINGS = "sceneMappings";
    /** 健康详情 key：供应商构建操作失败表（provider → 净化后的异常消息） */
    private static final String DETAIL_PROVIDER_ERRORS = "providerErrors";

    private final SceneRegistry sceneRegistry;
    private final List<AuditOperationProvider> providers;
    private final CircuitBreakerRegistry breakers;
    private final RateLimiterRegistry limiters;

    public ContentSafeHealthIndicator(SceneRegistry sceneRegistry,
                                      List<AuditOperationProvider> providers,
                                      CircuitBreakerRegistry breakers,
                                      RateLimiterRegistry limiters) {
        this.sceneRegistry = sceneRegistry;
        this.providers = providers == null ? List.of() : List.copyOf(providers);
        this.breakers = breakers;
        this.limiters = limiters;
    }

    @Override
    public Health health() {
        Map<String, CircuitBreaker.State> states = breakers.snapshot();
        List<String> open = states.entrySet().stream()
                .filter(entry -> entry.getValue() == CircuitBreaker.State.OPEN)
                .map(Map.Entry::getKey)
                .toList();
        Health.Builder builder = open.isEmpty() ? Health.up() : Health.status(STATUS_DEGRADED);
        Map<String, String> providerErrors = new LinkedHashMap<>();
        return builder
                .withDetail(DETAIL_SCENES, sceneRegistry.all().size())
                .withDetail(DETAIL_PROVIDERS, providers.size())
                .withDetail(DETAIL_OPEN_CIRCUIT_BREAKERS, open)
                .withDetail(DETAIL_RATE_LIMITERS, limiters.snapshot())
                .withDetail(DETAIL_SCENE_MAPPINGS, sceneMappings(providerErrors))
                .withDetail(DETAIL_PROVIDER_ERRORS, providerErrors)
                .build();
    }

    /** 场景 → 已映射供应商列表；供应商构建操作失败按未映射处理，异常记入 providerErrors 暴露 */
    private Map<String, List<String>> sceneMappings(Map<String, String> providerErrors) {
        Map<String, List<String>> mappings = new LinkedHashMap<>();
        for (SceneDefinition scene : sceneRegistry.all().values()) {
            List<String> mapped = providers.stream()
                    .filter(provider -> {
                        try {
                            return !provider.operations(scene).isEmpty();
                        } catch (RuntimeException ex) {
                            // 健康端点不能因单个 provider 缺陷整体失败（Spring 约定），
                            // 但也不静默吞掉：记入详情暴露，消息净化防敏感片段泄漏
                            providerErrors.putIfAbsent(provider.provider(),
                                    ErrorMessages.sanitize(ex.getMessage()));
                            return false;
                        }
                    })
                    .map(AuditOperationProvider::provider)
                    .toList();
            mappings.put(scene.code(), mapped);
        }
        return mappings;
    }
}
