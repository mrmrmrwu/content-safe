package io.github.wrqtec.content.safe.provider.baidu;

import java.util.LinkedHashMap;
import java.util.Map;

import io.github.wrqtec.content.safe.api.AuditRequest;
import io.github.wrqtec.content.safe.api.ContentRef;
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
    void callbackSceneDefaultsSubEventsToAllConclusions() {
        // 官方默认只推 2,3（违规/疑似），PASS 任务永远等不到回调；notice-url 场景须兜底全量
        RecordingVideoClient client = new RecordingVideoClient();
        BaiduProviderProperties properties = properties(Map.of("short_video", "303"));
        properties.getExtra().put(BaiduProviderConstants.EXTRA_NOTICE_URL, "https://cb.example.com/notify");

        new BaiduAuditOperationProvider(properties, client)
                .operations(SceneDefinition.of("short_video", ContentType.VIDEO, Severity.LOW))
                .forEach(operation -> operation.submitAsync(videoRequest()));

        assertThat(client.subEvents).isEqualTo(BaiduProviderConstants.SUB_EVENTS_ALL);
    }

    @Test
    void explicitSubEventsOverrideIsPreserved() {
        RecordingVideoClient client = new RecordingVideoClient();
        BaiduProviderProperties properties = properties(Map.of("short_video", "303"));
        properties.getExtra().put(BaiduProviderConstants.EXTRA_NOTICE_URL, "https://cb.example.com/notify");
        properties.getExtra().put(BaiduProviderConstants.EXTRA_SUB_EVENTS, "2,3");

        new BaiduAuditOperationProvider(properties, client)
                .operations(SceneDefinition.of("short_video", ContentType.VIDEO, Severity.LOW))
                .forEach(operation -> operation.submitAsync(videoRequest()));

        assertThat(client.subEvents).isEqualTo("2,3");
    }

    @Test
    void pollingSceneLeavesSubEventsUnset() {
        // 未配 notice-url 时不兜底（拉取路径固定请求全量结论）
        RecordingVideoClient client = new RecordingVideoClient();

        new BaiduAuditOperationProvider(properties(Map.of("short_video", "303")), client)
                .operations(SceneDefinition.of("short_video", ContentType.VIDEO, Severity.LOW))
                .forEach(operation -> operation.submitAsync(videoRequest()));

        assertThat(client.subEvents).isNull();
    }

    private static AuditRequest videoRequest() {
        return AuditRequest.builder()
                .scene("short_video").type(ContentType.VIDEO)
                .content(ContentRef.url("https://media.example.com/demo.mp4")).dataId("data-req")
                .build();
    }

    /** 记录 videoSubmit 收到的 subEvents（其余端点不参与本组断言） */
    private static final class RecordingVideoClient implements BaiduCensorClient {

        private String subEvents;

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
            this.subEvents = subEvents;
            BaiduVideoSubmitResponse response = new BaiduVideoSubmitResponse();
            response.setRet("0");
            BaiduVideoSubmitResponse.Data data = new BaiduVideoSubmitResponse.Data();
            data.setTaskId(456L);
            response.setData(data);
            return response;
        }

        @Override
        public BaiduVideoPullResponse videoPull(String taskId) {
            throw new UnsupportedOperationException();
        }
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
