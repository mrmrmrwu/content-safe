package io.github.wrqtec.content.safe.provider.volcengine.internal;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.volcengine.model.request.ImageRiskResultRequest;
import com.volcengine.model.request.RiskDetectionRequest;
import com.volcengine.model.response.AsyncRiskDetectionResponse;
import com.volcengine.model.response.VideoRiskResultResponse;
import io.github.wrqtec.content.safe.api.AuditRequest;
import io.github.wrqtec.content.safe.api.AuditResult;
import io.github.wrqtec.content.safe.api.ContentRef;
import io.github.wrqtec.content.safe.api.RiskHit;
import io.github.wrqtec.content.safe.api.enums.AuditSource;
import io.github.wrqtec.content.safe.api.enums.ContentType;
import io.github.wrqtec.content.safe.api.enums.RiskSuggestion;
import io.github.wrqtec.content.safe.api.exception.InvalidRequestException;
import io.github.wrqtec.content.safe.api.exception.ProviderCallException;
import io.github.wrqtec.content.safe.api.exception.UnsupportedContentException;
import io.github.wrqtec.content.safe.api.exception.UnsupportedFeatureException;
import io.github.wrqtec.content.safe.model.AsyncTaskRef;
import io.github.wrqtec.content.safe.model.ProviderPlan;
import io.github.wrqtec.content.safe.model.SceneDefinition;
import io.github.wrqtec.content.safe.provider.volcengine.VolcengineAuditOperationProvider;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * 火山引擎视频异步操作单元测试：Mockito 桩 invoker，验证能力位声明、
 * 提交/查询参数装配、中间态分流与脱敏红线（帧 URL/帧内文字/语音转写原文
 * 不透传）。终态映射走真实 Normalizer + 契约样本
 *
 * @author L.David
 */
class VolcengineVideoAsyncOperationTest {

    private static final Gson GSON = new Gson();

    private final VolcengineClientInvoker invoker = Mockito.mock(VolcengineClientInvoker.class);
    private final VolcengineAuditResultNormalizer normalizer = new VolcengineAuditResultNormalizer();

    private final ProviderPlan plan = VolcengineAuditOperationProvider.videoPlan("svc-short-video");

    private final SceneDefinition scene = new SceneDefinition("short_video", ContentType.VIDEO,
            null, null, Map.of());

    private VolcengineVideoAsyncOperation operation() {
        return new VolcengineVideoAsyncOperation(plan, 6801, scene,
                "videoUrl", "dataId", invoker, normalizer);
    }

    @Test
    void capabilitiesDeclareUrlOnlyAsyncQuery() {
        var capabilities = operation().capabilities();
        assertThat(capabilities.contentTypes()).containsExactly(ContentType.VIDEO);
        assertThat(capabilities.sync()).isFalse();
        assertThat(capabilities.url()).isTrue();
        assertThat(capabilities.asyncQuery()).isTrue();
        assertThat(capabilities.asyncCallback()).isFalse();
    }

    @Test
    void auditIsNotSupported() {
        assertThatThrownBy(() -> operation().audit(AuditRequest.video("short_video",
                        ContentRef.url("https://cdn.example.com/v.mp4"))))
                .isInstanceOf(UnsupportedFeatureException.class)
                .hasMessageContaining("synchronous audit");
    }

    @Test
    void submitAsyncRejectsNonVideoContent() {
        assertThatThrownBy(() -> operation().submitAsync(
                        AuditRequest.image("avatar", ContentRef.base64("aGVsbG8="))))
                .isInstanceOf(UnsupportedContentException.class)
                .hasMessageContaining("url content only");
        Mockito.verifyNoInteractions(invoker);
    }

    @Test
    void submitAsyncRejectsNonUrlContent() {
        assertThatThrownBy(() -> operation().submitAsync(
                        AuditRequest.video("short_video",
                                ContentRef.base64("aGVsbG8="))))
                .isInstanceOf(UnsupportedContentException.class)
                .hasMessageContaining("url content only");
        Mockito.verifyNoInteractions(invoker);
    }

    @Test
    void submitAsyncAssemblesEnvelopeAndReturnsTaskKey() throws Exception {
        when(invoker.asyncVideoRisk(any())).thenReturn(submitReceipt());
        AuditRequest request = AuditRequest.builder()
                .scene("short_video").type(ContentType.VIDEO)
                .content(ContentRef.url("https://cdn.example.com/v.mp4"))
                .dataId("data-req-1")
                .build();

        AsyncTaskRef ref = operation().submitAsync(request);

        assertThat(ref.providerTaskId()).isEqualTo("data-req-1");
        assertThat(ref.dataId()).isEqualTo("data-req-1");
        ArgumentCaptor<RiskDetectionRequest> captor = ArgumentCaptor.forClass(RiskDetectionRequest.class);
        Mockito.verify(invoker).asyncVideoRisk(captor.capture());
        assertThat(captor.getValue().getAppId()).isEqualTo(6801);
        assertThat(captor.getValue().getService()).isEqualTo("svc-short-video");
        JsonObject parameters = JsonParser.parseString(captor.getValue().getParameters()).getAsJsonObject();
        assertThat(parameters.get("videoUrl").getAsString()).isEqualTo("https://cdn.example.com/v.mp4");
        assertThat(parameters.get("dataId").getAsString()).isEqualTo("data-req-1");
    }

    @Test
    void submitAsyncGeneratesTaskKeyWhenDataIdMissing() throws Exception {
        when(invoker.asyncVideoRisk(any())).thenReturn(submitReceipt());
        AuditRequest request = AuditRequest.video("short_video",
                ContentRef.url("https://cdn.example.com/v.mp4"));

        AsyncTaskRef ref = operation().submitAsync(request);

        assertThat(ref.providerTaskId()).hasSize(32).doesNotContain("-");
        assertThat(ref.dataId()).isEqualTo(ref.providerTaskId());
    }

    @Test
    void submitAsyncFailsWhenReceiptEmpty() throws Exception {
        when(invoker.asyncVideoRisk(any())).thenReturn(new AsyncRiskDetectionResponse());
        assertThatThrownBy(() -> operation().submitAsync(AuditRequest.video("short_video",
                        ContentRef.url("https://cdn.example.com/v.mp4"))))
                .isInstanceOf(ProviderCallException.class)
                .hasMessageContaining("empty result");
    }

    @Test
    void queryInProgressWhenNoConclusionData() throws Exception {
        when(invoker.videoResult(any())).thenReturn(GSON.fromJson("""
                {"result": {"requestId": "req-video-query-001", "code": 200}}
                """, VideoRiskResultResponse.class));

        AuditResult result = operation().query(new AsyncTaskRef("data-req-1", "data-req-1"));

        assertThat(result.ok()).isTrue();
        assertThat(result.suggestion()).isNull();
        assertThat(result.finalResult()).isFalse();
        assertThat(result.source()).isEqualTo(AuditSource.ASYNC_QUERY);
        assertThat(result.requestId()).isEqualTo("req-video-query-001");
        assertThat(result.hits()).isEmpty();
    }

    @Test
    void queryFinishMapsFrameAndAudioHitsWithoutSensitivePayload() throws Exception {
        when(invoker.videoResult(any())).thenReturn(loadFinishResponse());

        AuditResult result = operation().query(new AsyncTaskRef("data-req-1", "data-req-1"));

        assertThat(result.ok()).isTrue();
        assertThat(result.suggestion()).isEqualTo(RiskSuggestion.REJECT);
        assertThat(result.finalResult()).isTrue();
        assertThat(result.hits()).hasSize(2);

        RiskHit frameHit = result.hits().get(0);
        assertThat(frameHit.labelPath()).isEqualTo("porn/sexy");
        assertThat(frameHit.raw().get("offset")).isEqualTo(12.5f);
        assertThat(frameHit.raw().get("sliceId")).isEqualTo("slice-001");

        RiskHit audioHit = result.hits().get(1);
        assertThat(audioHit.labelPath()).isEqualTo("ad/contact");
        assertThat(String.valueOf(audioHit.raw().get("matchedWords"))).contains("qq12345");

        // 帧签名 URL、帧内文字、语音转写原文绝不进入归一化结果
        String serialized = String.valueOf(result);
        assertThat(serialized).doesNotContain("signed.example.com");
        assertThat(serialized).doesNotContain("zz00112233");
        assertThat(serialized).doesNotContain("不应透传");
    }

    @Test
    void queryFailsWhenResultEmpty() throws Exception {
        when(invoker.videoResult(any())).thenReturn(new VideoRiskResultResponse());
        assertThatThrownBy(() -> operation().query(new AsyncTaskRef("data-req-1", "data-req-1")))
                .isInstanceOf(ProviderCallException.class)
                .hasMessageContaining("empty result");
    }

    @Test
    void queryRequiresTaskReference() {
        assertThatThrownBy(() -> operation().query(new AsyncTaskRef(" ", "data-req-1")))
                .isInstanceOf(InvalidRequestException.class);
        Mockito.verifyNoInteractions(invoker);
    }

    @Test
    void queryCarriesDataIdForCorrelation() throws Exception {
        when(invoker.videoResult(any())).thenReturn(GSON.fromJson("""
                {"result": {"requestId": "req-video-query-002", "code": 200}}
                """, VideoRiskResultResponse.class));

        operation().query(new AsyncTaskRef("data-req-1", "data-req-1"));

        ArgumentCaptor<ImageRiskResultRequest> captor =
                ArgumentCaptor.forClass(ImageRiskResultRequest.class);
        Mockito.verify(invoker).videoResult(captor.capture());
        assertThat(captor.getValue().getAppId()).isEqualTo(6801);
        assertThat(captor.getValue().getService()).isEqualTo("svc-short-video");
        assertThat(captor.getValue().getDataId()).isEqualTo("data-req-1");
    }

    private AsyncRiskDetectionResponse submitReceipt() {
        return GSON.fromJson("""
                {"result": {"requestId": "req-video-submit-001", "code": 200}}
                """, AsyncRiskDetectionResponse.class);
    }

    private VideoRiskResultResponse loadFinishResponse() {
        try (InputStream input = getClass().getResourceAsStream("/contracts/volcengine/video-finish.json")) {
            assertThat(input).as("contract sample video-finish.json exists").isNotNull();
            return GSON.fromJson(new String(input.readAllBytes(), StandardCharsets.UTF_8),
                    VideoRiskResultResponse.class);
        } catch (IOException e) {
            throw new IllegalStateException("failed to load contract sample video-finish.json", e);
        }
    }
}
