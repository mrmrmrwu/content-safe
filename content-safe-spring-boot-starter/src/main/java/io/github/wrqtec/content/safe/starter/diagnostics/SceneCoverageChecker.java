package io.github.wrqtec.content.safe.starter.diagnostics;

import java.util.ArrayList;
import java.util.List;

import io.github.wrqtec.content.safe.core.SceneRegistry;
import io.github.wrqtec.content.safe.model.SceneDefinition;
import io.github.wrqtec.content.safe.spi.AuditOperation;
import io.github.wrqtec.content.safe.spi.AuditOperationProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.SmartInitializingSingleton;

/**
 * 启动期场景覆盖检查：所有单例就绪后逐一检查场景是否已有供应商映射，
 * 缺口提前告警，避免业务首次调用才抛 {@code ProviderNotAvailableException}
 *
 * @author L.David
 */
@Slf4j
public class SceneCoverageChecker implements SmartInitializingSingleton {

    private final SceneRegistry sceneRegistry;

    private final List<AuditOperationProvider> providers;

    public SceneCoverageChecker(SceneRegistry sceneRegistry, List<AuditOperationProvider> providers) {
        this.sceneRegistry = sceneRegistry;
        this.providers = List.copyOf(providers);
    }

    @Override
    public void afterSingletonsInstantiated() {
        for (SceneDefinition scene : sceneRegistry.all().values()) {
            if (operationsOf(scene).isEmpty()) {
                log.warn("scene [{}] has no provider operations mapped; audit() for it will fail"
                        + " until a provider maps it in its scenes config", scene.code());
            }
        }
    }

    private List<AuditOperation> operationsOf(SceneDefinition scene) {
        List<AuditOperation> operations = new ArrayList<>();
        for (AuditOperationProvider provider : providers) {
            try {
                operations.addAll(provider.operations(scene));
            } catch (RuntimeException ex) {
                log.warn("provider [{}] failed to build operations for scene [{}]: {}",
                        provider.provider(), scene.code(), ex.getMessage());
            }
        }
        return operations;
    }
}
