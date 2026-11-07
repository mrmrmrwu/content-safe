package io.github.wrqtec.content.safe.provider.shumei;

import java.util.LinkedHashMap;
import java.util.Map;

import io.github.wrqtec.content.safe.api.enums.ContentType;
import io.github.wrqtec.content.safe.api.enums.Severity;
import io.github.wrqtec.content.safe.model.SceneDefinition;
import io.github.wrqtec.content.safe.model.enums.ProviderLifecycle;
import io.github.wrqtec.content.safe.provider.shumei.ShumeiAuditOperationProvider;
import io.github.wrqtec.content.safe.provider.shumei.internal.ShumeiClientInvoker;
import io.github.wrqtec.content.safe.provider.shumei.internal.ShumeiLimits;
import io.github.wrqtec.content.safe.provider.shumei.internal.ShumeiSyncResponse;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link ShumeiAuditOperationProvider} 单元测试
 *
 * @author L.David
 */
class ShumeiAuditOperationProviderTest {

    private static final ShumeiClientInvoker NOOP_CLIENT = new ShumeiClientInvoker() {
        @Override
        public ShumeiSyncResponse textDetect(String eventId, String type, Map<String, Object> data) {
            throw new UnsupportedOperationException();
        }

        @Override
        public ShumeiSyncResponse imageDetect(String eventId, String type, Map<String, Object> data) {
            throw new UnsupportedOperationException();
        }

        @Override
        public ShumeiSyncResponse imageAsyncSubmit(String eventId, String type, Map<String, Object> data) {
            throw new UnsupportedOperationException();
        }

        @Override
        public io.github.wrqtec.content.safe.provider.shumei.internal.ShumeiAsyncQueryResponse imageAsyncQuery(
                java.util.List<String> requestIds) {
            throw new UnsupportedOperationException();
        }
    };

    @Test
    void unmappedSceneReturnsNoOperations() {
        ShumeiAuditOperationProvider provider = new ShumeiAuditOperationProvider(
                properties(Map.of()), NOOP_CLIENT);

        assertThat(provider.operations(SceneDefinition.of("comment", ContentType.TEXT, Severity.LOW)))
                .isEmpty();
    }

    @Test
    void mappedTextSceneReturnsTextOperationWithEventId() {
        ShumeiAuditOperationProvider provider = new ShumeiAuditOperationProvider(
                properties(Map.of("comment", "text")), NOOP_CLIENT);

        assertThat(provider.operations(SceneDefinition.of("comment", ContentType.TEXT, Severity.LOW)))
                .hasSize(1)
                .first()
                .satisfies(operation -> {
                    assertThat(operation.plan().operation()).isEqualTo("TextDetect");
                    assertThat(operation.plan().service()).isEqualTo("text");
                    assertThat(operation.plan().mode()).isEqualTo(io.github.wrqtec.content.safe.model.enums.AuditMode.SYNC);
                });
    }

    @Test
    void mappedImageSceneReturnsSyncAndAsyncOperations() {
        ShumeiAuditOperationProvider provider = new ShumeiAuditOperationProvider(
                properties(Map.of("avatar", "image")), NOOP_CLIENT);

        assertThat(provider.operations(SceneDefinition.of("avatar", ContentType.IMAGE, Severity.LOW)))
                .hasSize(2)
                .satisfies(operations -> {
                    assertThat(operations.get(0).plan().operation()).isEqualTo("ImageDetect");
                    assertThat(operations.get(0).plan().service()).isEqualTo("image");
                    assertThat(operations.get(0).plan().mode())
                            .isEqualTo(io.github.wrqtec.content.safe.model.enums.AuditMode.SYNC);
                    assertThat(operations.get(1)).isInstanceOf(
                            io.github.wrqtec.content.safe.provider.shumei.internal.ShumeiImageAsyncOperation.class);
                    assertThat(operations.get(1).plan().operation()).isEqualTo("ImageAsyncSubmit");
                    assertThat(operations.get(1).plan().mode())
                            .isEqualTo(io.github.wrqtec.content.safe.model.enums.AuditMode.ASYNC_QUERY);
                    assertThat(operations.get(1).capabilities().asyncQuery()).isTrue();
                    assertThat(operations.get(1).capabilities().asyncCallback()).isTrue();
                });
    }

    @Test
    void typeMismatchReturnsNoOperations() {
        ShumeiAuditOperationProvider provider = new ShumeiAuditOperationProvider(
                properties(Map.of("media", "video")), NOOP_CLIENT);

        assertThat(provider.operations(SceneDefinition.of("media", ContentType.VIDEO, Severity.LOW)))
                .isEmpty();
    }

    @Test
    void declaresSupportedTypes() {
        ShumeiAuditOperationProvider provider = new ShumeiAuditOperationProvider(properties(Map.of()), NOOP_CLIENT);

        assertThat(provider.provider()).isEqualTo("shumei");
        assertThat(provider.supportedTypes()).containsExactly(ContentType.TEXT, ContentType.IMAGE);
    }

    @Test
    void extraTypeOverridesDefaults() {
        Map<String, String> extra = new LinkedHashMap<>();
        extra.put("text-type", "POLITY_EROTIC");
        extra.put("image-type", "POLITY");
        ShumeiProviderProperties properties = properties(Map.of("comment", "text", "avatar", "image"));
        properties.setExtra(extra);
        ShumeiAuditOperationProvider provider = new ShumeiAuditOperationProvider(properties, NOOP_CLIENT);

        assertThat(provider.operations(SceneDefinition.of("comment", ContentType.TEXT, Severity.LOW)))
                .first()
                .satisfies(operation -> assertThat(operation).isInstanceOf(
                        io.github.wrqtec.content.safe.provider.shumei.internal.ShumeiTextDetectOperation.class));
        assertThat(provider.operations(SceneDefinition.of("avatar", ContentType.IMAGE, Severity.LOW)))
                .first()
                .satisfies(operation -> assertThat(operation).isInstanceOf(
                        io.github.wrqtec.content.safe.provider.shumei.internal.ShumeiImageDetectOperation.class));
    }

    @Test
    void syncPlanCarriesDefaults() {
        io.github.wrqtec.content.safe.model.ProviderPlan plan =
                ShumeiAuditOperationProvider.syncPlan("TextDetect", "text", ShumeiLimits.textSync());

        assertThat(plan.provider()).isEqualTo("shumei");
        assertThat(plan.product()).isEqualTo("shumei-content-security");
        assertThat(plan.apiVersion()).isEqualTo("v4");
        assertThat(plan.operation()).isEqualTo("TextDetect");
        assertThat(plan.service()).isEqualTo("text");
        assertThat(plan.lifecycle()).isEqualTo(ProviderLifecycle.ACTIVE);
    }

    @Test
    void asyncPlanCarriesAsyncDefaults() {
        io.github.wrqtec.content.safe.model.ProviderPlan plan =
                ShumeiAuditOperationProvider.asyncPlan("image");

        assertThat(plan.operation()).isEqualTo("ImageAsyncSubmit");
        assertThat(plan.service()).isEqualTo("image");
        assertThat(plan.mode()).isEqualTo(io.github.wrqtec.content.safe.model.enums.AuditMode.ASYNC_QUERY);
        assertThat(plan.lifecycle()).isEqualTo(ProviderLifecycle.ACTIVE);
    }

    private static ShumeiProviderProperties properties(Map<String, String> scenes) {
        ShumeiProviderProperties properties = new ShumeiProviderProperties();
        properties.setScenes(new LinkedHashMap<>(scenes));
        return properties;
    }
}
