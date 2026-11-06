package io.github.wrqtec.content.safe.provider.tencent;

import java.util.List;
import java.util.Map;

import io.github.wrqtec.content.safe.api.enums.ContentType;
import io.github.wrqtec.content.safe.api.enums.Severity;
import io.github.wrqtec.content.safe.model.SceneDefinition;
import io.github.wrqtec.content.safe.spi.AuditOperation;
import io.github.wrqtec.content.safe.provider.tencent.internal.TencentAuditResultNormalizer;
import io.github.wrqtec.content.safe.provider.tencent.internal.TencentClientInvoker;
import io.github.wrqtec.content.safe.provider.tencent.internal.TencentImageModerationOperation;
import io.github.wrqtec.content.safe.provider.tencent.internal.TencentTextModerationOperation;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * {@link TencentAuditOperationProvider} 单元测试（场景映射规则）
 *
 * @author L.David
 */
class TencentAuditOperationProviderTest {

    private final TencentClientInvoker invoker = mock(TencentClientInvoker.class);
    private final TencentProviderProperties properties = new TencentProviderProperties();
    private final TencentAuditOperationProvider provider =
            new TencentAuditOperationProvider(properties, invoker, new TencentAuditResultNormalizer());

    @Test
    void declaresProviderAndTypes() {
        assertThat(provider.provider()).isEqualTo("tencent");
        assertThat(provider.supportedTypes()).containsExactly(ContentType.TEXT, ContentType.IMAGE);
    }

    @Test
    void unmappedSceneReturnsEmpty() {
        List<AuditOperation> operations =
                provider.operations(SceneDefinition.of("unknown", ContentType.TEXT, Severity.LOW));
        assertThat(operations).isEmpty();
    }

    @Test
    void mappedTextSceneBuildsTextOperation() {
        properties.getScenes().put("comment", "biz-comment");
        List<AuditOperation> operations =
                provider.operations(SceneDefinition.of("comment", ContentType.TEXT, Severity.MEDIUM));
        assertThat(operations).hasSize(1);
        assertThat(operations.get(0)).isInstanceOf(TencentTextModerationOperation.class);
        assertThat(operations.get(0).plan().service()).isEqualTo("biz-comment");
        assertThat(operations.get(0).plan().provider()).isEqualTo("tencent");
    }

    @Test
    void mappedImageSceneBuildsImageOperation() {
        properties.getScenes().put("avatar", "biz-avatar");
        List<AuditOperation> operations =
                provider.operations(SceneDefinition.of("avatar", ContentType.IMAGE, Severity.HIGH));
        assertThat(operations).hasSize(1);
        assertThat(operations.get(0)).isInstanceOf(TencentImageModerationOperation.class);
    }

    @Test
    void aigcSceneOnlyAppliesToImage() {
        properties.getAigcScenes().put("ai-art", "biz-aigc");
        List<AuditOperation> imageOps =
                provider.operations(SceneDefinition.of("ai-art", ContentType.IMAGE, Severity.MEDIUM));
        assertThat(imageOps).hasSize(1);
        assertThat(imageOps.get(0)).isInstanceOf(TencentImageModerationOperation.class);

        List<AuditOperation> textOps =
                provider.operations(SceneDefinition.of("ai-art", ContentType.TEXT, Severity.MEDIUM));
        assertThat(textOps).isEmpty();
    }

    @Test
    void gifParamsParsedFromExtra() {
        properties.getScenes().put("gif", "biz-gif");
        properties.getExtra().putAll(Map.of("gifInterval", "3", "gifMaxFrames", "8",
                "badInt", "abc"));
        List<AuditOperation> operations =
                provider.operations(SceneDefinition.of("gif", ContentType.IMAGE, Severity.LOW));
        assertThat(operations).hasSize(1);
    }
}
