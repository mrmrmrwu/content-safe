package io.github.wrqtec.content.safe.provider.aliyun;

import java.util.LinkedHashMap;
import java.util.Map;

import io.github.wrqtec.content.safe.api.enums.ContentType;
import io.github.wrqtec.content.safe.model.SceneDefinition;
import io.github.wrqtec.content.safe.spi.AuditOperation;
import io.github.wrqtec.content.safe.provider.aliyun.internal.AliyunImageModerationOperation;
import io.github.wrqtec.content.safe.provider.aliyun.internal.AliyunTextModerationOperation;
import io.github.wrqtec.content.safe.provider.aliyun.internal.GreenClientInvoker;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * {@link AliyunAuditOperationProvider} 单元测试：场景映射驱动（铁律 §9.2 数据驱动路由）
 *
 * @author L.David
 */
class AliyunAuditOperationProviderTest {

    private final GreenClientInvoker invoker = mock(GreenClientInvoker.class);

    @Test
    void unmappedSceneYieldsNoOperations() {
        AliyunAuditOperationProvider provider = new AliyunAuditOperationProvider(invoker, properties(Map.of()));
        assertThat(provider.operations(SceneDefinition.of("unknown", ContentType.TEXT, null))).isEmpty();
    }

    @Test
    void textSceneYieldsTextModerationOperation() {
        AliyunAuditOperationProvider provider = new AliyunAuditOperationProvider(invoker,
                properties(Map.of("comment", "comment_detection")));
        var operations = provider.operations(SceneDefinition.of("comment", ContentType.TEXT, null));
        assertThat(operations).hasSize(1);
        AuditOperation operation = operations.get(0);
        assertThat(operation).isInstanceOf(AliyunTextModerationOperation.class);
        assertThat(operation.plan().operation()).isEqualTo(AliyunProviderConstants.OPERATION_TEXT_MODERATION);
        assertThat(operation.plan().service()).isEqualTo("comment_detection");
        assertThat(operation.plan().planKey()).isEqualTo("aliyun:TextModeration");
    }

    @Test
    void imageSceneYieldsImageModerationOperation() {
        AliyunAuditOperationProvider provider = new AliyunAuditOperationProvider(invoker,
                properties(Map.of("avatar", "baselineCheck")));
        var operations = provider.operations(SceneDefinition.of("avatar", ContentType.IMAGE, null));
        assertThat(operations).hasSize(1);
        AuditOperation operation = operations.get(0);
        assertThat(operation).isInstanceOf(AliyunImageModerationOperation.class);
        assertThat(operation.plan().operation()).isEqualTo(AliyunProviderConstants.OPERATION_IMAGE_MODERATION);
    }

    @Test
    void mappedButTypeMismatchYieldsNoOperations() {
        AliyunAuditOperationProvider provider = new AliyunAuditOperationProvider(invoker,
                properties(Map.of("comment", "comment_detection")));
        // 场景已映射但内容类型既不是 TEXT 也不是 IMAGE（如 AUDIO，v1 不承诺）
        assertThat(provider.operations(SceneDefinition.of("comment", ContentType.AUDIO, null))).isEmpty();
    }

    @Test
    void declaresSupportedTypes() {
        AliyunAuditOperationProvider provider = new AliyunAuditOperationProvider(invoker, properties(Map.of()));
        assertThat(provider.provider()).isEqualTo("aliyun");
        assertThat(provider.supportedTypes()).containsExactly(ContentType.TEXT, ContentType.IMAGE);
    }

    private static AliyunProviderProperties properties(Map<String, String> scenes) {
        AliyunProviderProperties properties = new AliyunProviderProperties();
        properties.setScenes(new LinkedHashMap<>(scenes));
        return properties;
    }
}
