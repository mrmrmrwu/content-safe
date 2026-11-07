package io.github.wrqtec.content.safe.starter.diagnostics;

import java.util.List;
import java.util.function.Function;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import io.github.wrqtec.content.safe.api.enums.ContentType;
import io.github.wrqtec.content.safe.core.SceneRegistry;
import io.github.wrqtec.content.safe.model.SceneDefinition;
import io.github.wrqtec.content.safe.spi.AuditOperation;
import io.github.wrqtec.content.safe.spi.AuditOperationProvider;
import io.github.wrqtec.content.safe.spi.SceneDefinitionProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.slf4j.LoggerFactory;

import static org.assertj.core.api.Assertions.assertThat;

class SceneCoverageCheckerTest {

    private ListAppender<ILoggingEvent> logAppender;

    private Level originalLevel;

    @BeforeEach
    void attachLogCapture() {
        Logger logger = (Logger) LoggerFactory.getLogger(SceneCoverageChecker.class);
        logAppender = new ListAppender<>();
        logAppender.start();
        logger.addAppender(logAppender);
        originalLevel = logger.getLevel();
        logger.setLevel(Level.WARN);
    }

    @AfterEach
    void detachLogCapture() {
        Logger logger = (Logger) LoggerFactory.getLogger(SceneCoverageChecker.class);
        logger.detachAppender(logAppender);
        logger.setLevel(originalLevel);
    }

    @Test
    void unmappedSceneWarnsAtStartup() {
        SceneRegistry registry = new SceneRegistry(List.of());
        AuditOperation mappedOp = Mockito.mock(AuditOperation.class);
        AuditOperationProvider partial = providerOf(scene -> "nickname".equals(scene.code())
                ? List.of(mappedOp) : List.of());
        SceneCoverageChecker checker = new SceneCoverageChecker(registry, List.of(partial));

        checker.afterSingletonsInstantiated();

        assertThat(logAppender.list)
                .anyMatch(event -> event.getLevel() == Level.WARN
                        && event.getFormattedMessage().contains("comment"));
    }

    @Test
    void fullyMappedSceneDoesNotWarn() {
        AuditOperation op = Mockito.mock(AuditOperation.class);
        SceneDefinitionProvider customProvider = () -> List.of(
                SceneDefinition.of("custom_scene", ContentType.TEXT, null));
        SceneRegistry registry = new SceneRegistry(List.of(customProvider));
        AuditOperationProvider full = providerOf(scene -> List.of(op));
        SceneCoverageChecker checker = new SceneCoverageChecker(registry, List.of(full));

        checker.afterSingletonsInstantiated();

        assertThat(logAppender.list).isEmpty();
    }

    @Test
    void providerFailureTreatedAsUnmapped() {
        SceneRegistry registry = new SceneRegistry(List.of());
        AuditOperationProvider broken = new AuditOperationProvider() {
            @Override
            public String provider() {
                return "broken";
            }

            @Override
            public List<ContentType> supportedTypes() {
                return List.of();
            }

            @Override
            public List<AuditOperation> operations(SceneDefinition scene) {
                throw new IllegalStateException("misconfigured provider");
            }
        };
        SceneCoverageChecker checker = new SceneCoverageChecker(registry, List.of(broken));

        checker.afterSingletonsInstantiated();

        // 供应商装配异常降级为告警，不阻断启动，也不冒充场景已映射
        assertThat(logAppender.list)
                .anyMatch(event -> event.getLevel() == Level.WARN
                        && event.getFormattedMessage().contains("broken"));
    }

    private AuditOperationProvider providerOf(Function<SceneDefinition, List<AuditOperation>> ops) {
        return new AuditOperationProvider() {
            @Override
            public String provider() {
                return "fake";
            }

            @Override
            public List<ContentType> supportedTypes() {
                return List.of();
            }

            @Override
            public List<AuditOperation> operations(SceneDefinition scene) {
                return ops.apply(scene);
            }
        };
    }
}
