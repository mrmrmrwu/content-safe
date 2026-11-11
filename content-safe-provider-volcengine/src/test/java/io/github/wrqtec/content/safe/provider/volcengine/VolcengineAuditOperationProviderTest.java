package io.github.wrqtec.content.safe.provider.volcengine;

import java.util.List;
import java.util.Map;

import io.github.wrqtec.content.safe.api.enums.ContentType;
import io.github.wrqtec.content.safe.api.exception.ConfigInvalidException;
import io.github.wrqtec.content.safe.model.OperationLimits;
import io.github.wrqtec.content.safe.model.ProviderPlan;
import io.github.wrqtec.content.safe.model.SceneDefinition;
import io.github.wrqtec.content.safe.model.enums.AuditMode;
import io.github.wrqtec.content.safe.provider.volcengine.internal.VolcengineAuditResultNormalizer;
import io.github.wrqtec.content.safe.provider.volcengine.internal.VolcengineClientInvoker;
import io.github.wrqtec.content.safe.provider.volcengine.internal.VolcengineImageRiskOperation;
import io.github.wrqtec.content.safe.provider.volcengine.internal.VolcengineTextRiskOperation;
import io.github.wrqtec.content.safe.provider.volcengine.internal.VolcengineVideoAsyncOperation;
import io.github.wrqtec.content.safe.spi.AuditOperation;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class VolcengineAuditOperationProviderTest {

    private final VolcengineClientInvoker invoker = Mockito.mock(VolcengineClientInvoker.class);

    private VolcengineProviderProperties properties(String textKey, String imageKey) {
        VolcengineProviderProperties properties = new VolcengineProviderProperties();
        properties.getScenes().put("nickname", "svc-nickname");
        properties.getScenes().put("avatar", "svc-avatar");
        properties.getExtra().put(VolcengineProviderConstants.EXTRA_APP_ID, "6801");
        if (textKey != null) {
            properties.getExtra().put(VolcengineProviderConstants.EXTRA_TEXT_PARAMETER_KEY, textKey);
        }
        if (imageKey != null) {
            properties.getExtra().put(VolcengineProviderConstants.EXTRA_IMAGE_PARAMETER_KEY, imageKey);
        }
        return properties;
    }

    @Test
    void textSceneMapsToTextRiskOperation() {
        VolcengineAuditOperationProvider provider =
                new VolcengineAuditOperationProvider(properties("text", "image"), invoker,
                        new VolcengineAuditResultNormalizer());
        List<AuditOperation> operations = provider.operations(
                SceneDefinition.of("nickname", ContentType.TEXT, null));
        assertThat(operations).hasSize(1);
        ProviderPlan plan = operations.get(0).plan();
        assertThat(plan.provider()).isEqualTo("volcengine");
        assertThat(plan.operation()).isEqualTo("TextRisk");
        assertThat(plan.apiVersion()).isEqualTo("2022-01-26");
        assertThat(plan.service()).isEqualTo("svc-nickname");
        assertThat(plan.product()).isEqualTo("volcengine-content-security");
    }

    @Test
    void imageSceneMapsToImageContentRiskV2Operation() {
        VolcengineAuditOperationProvider provider =
                new VolcengineAuditOperationProvider(properties("text", "image"), invoker,
                        new VolcengineAuditResultNormalizer());
        List<AuditOperation> operations = provider.operations(
                SceneDefinition.of("avatar", ContentType.IMAGE, null));
        assertThat(operations).hasSize(1);
        ProviderPlan plan = operations.get(0).plan();
        assertThat(plan.operation()).isEqualTo("ImageContentRiskV2");
        assertThat(plan.apiVersion()).isEqualTo("2021-11-29");
        assertThat(plan.service()).isEqualTo("svc-avatar");
        assertThat(operations.get(0)).isInstanceOf(VolcengineImageRiskOperation.class);
    }

    @Test
    void unmappedSceneReturnsEmpty() {
        VolcengineAuditOperationProvider provider =
                new VolcengineAuditOperationProvider(properties("text", "image"), invoker,
                        new VolcengineAuditResultNormalizer());
        assertThat(provider.operations(SceneDefinition.of("comment", ContentType.TEXT, null))).isEmpty();
    }

    @Test
    void missingTextKeyFailsFastForTextSceneOnly() {
        VolcengineAuditOperationProvider provider =
                new VolcengineAuditOperationProvider(properties(null, "image"), invoker,
                        new VolcengineAuditResultNormalizer());
        assertThatThrownBy(() -> provider.operations(SceneDefinition.of("nickname", ContentType.TEXT, null)))
                .isInstanceOf(ConfigInvalidException.class)
                .hasMessageContaining("parameters-text-key");
    }

    @Test
    void missingAppIdFailsAtConstruction() {
        VolcengineProviderProperties properties = properties("text", "image");
        properties.getExtra().remove(VolcengineProviderConstants.EXTRA_APP_ID);
        assertThatThrownBy(() -> new VolcengineAuditOperationProvider(properties, invoker,
                new VolcengineAuditResultNormalizer()))
                .isInstanceOf(ConfigInvalidException.class)
                .hasMessageContaining("app-id");
    }

    @Test
    void syncPlanCarriesLifecycleAndLimits() {
        ProviderPlan plan = VolcengineAuditOperationProvider.syncPlan(
                "TextRisk", "svc", "2022-01-26", new OperationLimits(null, null,
                        java.time.Duration.ofSeconds(5), 0L, 0L, false));
        assertThat(plan.lifecycle().name()).isEqualTo("ACTIVE");
        assertThat(plan.mode().name()).isEqualTo("SYNC");
        assertThat(plan.planKey()).isEqualTo("volcengine:TextRisk");
        assertThat(plan).isInstanceOf(ProviderPlan.class);
    }

    @Test
    void textOperationTypeIsTextRiskOperation() {
        VolcengineAuditOperationProvider provider =
                new VolcengineAuditOperationProvider(properties("text", "image"), invoker,
                        new VolcengineAuditResultNormalizer());
        List<AuditOperation> operations = provider.operations(
                SceneDefinition.of("nickname", ContentType.TEXT, null));
        assertThat(operations.get(0)).isInstanceOf(VolcengineTextRiskOperation.class);
    }

    @Test
    void videoSceneMapsToVideoAsyncOperation() {
        VolcengineProviderProperties properties = properties("text", "image");
        properties.getScenes().put("short_video", "svc-video");
        properties.getExtra().put(VolcengineProviderConstants.EXTRA_VIDEO_PARAMETER_KEY, "videoUrl");
        VolcengineAuditOperationProvider provider =
                new VolcengineAuditOperationProvider(properties, invoker,
                        new VolcengineAuditResultNormalizer());

        List<AuditOperation> operations = provider.operations(
                SceneDefinition.of("short_video", ContentType.VIDEO, null));

        assertThat(operations).hasSize(1);
        assertThat(operations.get(0)).isInstanceOf(VolcengineVideoAsyncOperation.class);
        ProviderPlan plan = operations.get(0).plan();
        assertThat(plan.operation()).isEqualTo("AsyncVideoRisk");
        assertThat(plan.apiVersion()).isEqualTo("2021-11-29");
        assertThat(plan.mode()).isEqualTo(AuditMode.ASYNC_QUERY);
        assertThat(plan.service()).isEqualTo("svc-video");
        assertThat(operations.get(0).capabilities().asyncQuery()).isTrue();
        assertThat(operations.get(0).capabilities().asyncCallback()).isFalse();
    }

    @Test
    void missingVideoKeyFailsFastForVideoSceneOnly() {
        VolcengineProviderProperties properties = properties("text", "image");
        properties.getScenes().put("short_video", "svc-video");
        VolcengineAuditOperationProvider provider =
                new VolcengineAuditOperationProvider(properties, invoker,
                        new VolcengineAuditResultNormalizer());
        assertThatThrownBy(() -> provider.operations(
                SceneDefinition.of("short_video", ContentType.VIDEO, null)))
                .isInstanceOf(ConfigInvalidException.class)
                .hasMessageContaining("parameters-video-key");
    }

    @Test
    void videoPlanCarriesAsyncQueryMode() {
        ProviderPlan plan = VolcengineAuditOperationProvider.videoPlan("svc-video");
        assertThat(plan.planKey()).isEqualTo("volcengine:AsyncVideoRisk");
        assertThat(plan.mode()).isEqualTo(AuditMode.ASYNC_QUERY);
        assertThat(plan.apiVersion()).isEqualTo("2021-11-29");
        assertThat(plan.lifecycle().name()).isEqualTo("ACTIVE");
    }
}
