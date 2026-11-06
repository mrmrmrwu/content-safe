package io.github.wrqtec.content.safe.starter.actuator;

import java.util.List;
import java.util.Map;

import io.github.wrqtec.content.safe.core.SceneRegistry;
import io.github.wrqtec.content.safe.core.degrade.CircuitBreaker;
import io.github.wrqtec.content.safe.core.degrade.CircuitBreakerRegistry;
import io.github.wrqtec.content.safe.spi.AuditOperationProvider;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;

/**
 * ContentSafe 健康指示器。仅汇总本地状态（场景数、供应商数、熔断器快照），
 * 不发起任何远程探测。
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

    private final SceneRegistry sceneRegistry;
    private final List<AuditOperationProvider> providers;
    private final CircuitBreakerRegistry breakers;

    public ContentSafeHealthIndicator(SceneRegistry sceneRegistry,
                                      List<AuditOperationProvider> providers,
                                      CircuitBreakerRegistry breakers) {
        this.sceneRegistry = sceneRegistry;
        this.providers = providers == null ? List.of() : List.copyOf(providers);
        this.breakers = breakers;
    }

    @Override
    public Health health() {
        Map<String, CircuitBreaker.State> states = breakers.snapshot();
        List<String> open = states.entrySet().stream()
                .filter(entry -> entry.getValue() == CircuitBreaker.State.OPEN)
                .map(Map.Entry::getKey)
                .toList();
        Health.Builder builder = open.isEmpty() ? Health.up() : Health.status(STATUS_DEGRADED);
        return builder
                .withDetail(DETAIL_SCENES, sceneRegistry.all().size())
                .withDetail(DETAIL_PROVIDERS, providers.size())
                .withDetail(DETAIL_OPEN_CIRCUIT_BREAKERS, open)
                .build();
    }
}
