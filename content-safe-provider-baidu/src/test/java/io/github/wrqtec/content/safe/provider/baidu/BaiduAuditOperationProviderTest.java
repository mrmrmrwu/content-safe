package io.github.wrqtec.content.safe.provider.baidu;

import java.util.LinkedHashMap;
import java.util.Map;

import io.github.wrqtec.content.safe.api.enums.ContentType;
import io.github.wrqtec.content.safe.api.enums.Severity;
import io.github.wrqtec.content.safe.model.SceneDefinition;
import io.github.wrqtec.content.safe.model.enums.AuditMode;
import io.github.wrqtec.content.safe.provider.baidu.internal.BaiduCensorClient;
import io.github.wrqtec.content.safe.provider.baidu.internal.BaiduCensorResponse;
import io.github.wrqtec.content.safe.provider.baidu.internal.BaiduVideoAsyncOperation;
import io.github.wrqtec.content.safe.provider.baidu.internal.BaiduVideoPullResponse;
import io.github.wrqtec.content.safe.provider.baidu.internal.BaiduVideoSubmitResponse;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link BaiduAuditOperationProvider} 单元测试
 *
 * @author L.David
 */
class BaiduAuditOperationProviderTest {

    private static final BaiduCensorClient NOOP_CLIENT = new BaiduCensorClient() {
        @Override
        public BaiduCensorResponse textCensor(String text, String strategyId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public BaiduCensorResponse imageCensorByBase64(String base64, String strategyId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public BaiduCensorResponse imageCensorByUrl(String imgUrl, String strategyId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public BaiduVideoSubmitResponse videoSubmit(String url, String extId, String strategyId,
                                                    String noticeUrl, String subEvents) {
            throw new UnsupportedOperationException();
        }

        @Override
        public BaiduVideoPullResponse videoPull(String taskId) {
            throw new UnsupportedOperationException();
        }
    };

    @Test
    void unmappedSceneReturnsNoOperations() {
        BaiduAuditOperationProvider provider = new BaiduAuditOperationProvider(properties(Map.of()), NOOP_CLIENT);

        assertThat(provider.operations(SceneDefinition.of("comment", ContentType.TEXT, Severity.LOW))).isEmpty();
    }

    @Test
    void mappedTextSceneReturnsTextOperation() {
        BaiduAuditOperationProvider provider = new BaiduAuditOperationProvider(
                properties(Map.of("comment", "101")), NOOP_CLIENT);

        assertThat(provider.operations(SceneDefinition.of("comment", ContentType.TEXT, Severity.LOW)))
                .hasSize(1)
                .first()
                .satisfies(operation -> {
                    assertThat(operation.plan().operation()).isEqualTo("TextCensor");
                    assertThat(operation.plan().service()).isEqualTo("101");
                });
    }

    @Test
    void mappedImageSceneReturnsImageOperation() {
        BaiduAuditOperationProvider provider = new BaiduAuditOperationProvider(
                properties(Map.of("avatar", "202")), NOOP_CLIENT);

        assertThat(provider.operations(SceneDefinition.of("avatar", ContentType.IMAGE, Severity.LOW)))
                .hasSize(1)
                .first()
                .satisfies(operation ->
                        assertThat(operation.plan().operation()).isEqualTo("ImageCensor"));
    }

    @Test
    void blankStrategyIdStillRegistersWithDefaultStrategy() {
        BaiduAuditOperationProvider provider = new BaiduAuditOperationProvider(
                properties(Map.of("comment", "")), NOOP_CLIENT);

        assertThat(provider.operations(SceneDefinition.of("comment", ContentType.TEXT, Severity.LOW)))
                .hasSize(1)
                .first()
                .satisfies(operation -> assertThat(operation.plan().service()).isEmpty());
    }

    @Test
    void videoSceneYieldsVideoAsyncOperation() {
        BaiduAuditOperationProvider provider = new BaiduAuditOperationProvider(
                properties(Map.of("short_video", "303")), NOOP_CLIENT);

        assertThat(provider.operations(SceneDefinition.of("short_video", ContentType.VIDEO, Severity.LOW)))
                .hasSize(1)
                .first()
                .isInstanceOf(BaiduVideoAsyncOperation.class)
                .satisfies(operation -> {
                    assertThat(operation.plan().operation()).isEqualTo("VideoCensor");
                    assertThat(operation.plan().apiVersion()).isEqualTo("v1");
                    assertThat(operation.plan().mode()).isEqualTo(AuditMode.ASYNC_QUERY);
                });
    }

    @Test
    void declaresSupportedTypes() {
        BaiduAuditOperationProvider provider = new BaiduAuditOperationProvider(properties(Map.of()), NOOP_CLIENT);

        assertThat(provider.provider()).isEqualTo("baidu");
        assertThat(provider.supportedTypes()).containsExactly(ContentType.TEXT, ContentType.IMAGE, ContentType.VIDEO);
    }

    private static BaiduProviderProperties properties(Map<String, String> scenes) {
        BaiduProviderProperties properties = new BaiduProviderProperties();
        properties.setScenes(new LinkedHashMap<>(scenes));
        return properties;
    }
}
