package io.github.wrqtec.content.safe.provider.baidu.internal;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import com.google.gson.Gson;
import io.github.wrqtec.content.safe.api.AuditRequest;
import io.github.wrqtec.content.safe.api.AuditResult;
import io.github.wrqtec.content.safe.api.CallbackRequest;
import io.github.wrqtec.content.safe.api.CallbackResult;
import io.github.wrqtec.content.safe.api.ContentRef;
import io.github.wrqtec.content.safe.api.RiskHit;
import io.github.wrqtec.content.safe.api.enums.AuditSource;
import io.github.wrqtec.content.safe.api.enums.ConfidenceScale;
import io.github.wrqtec.content.safe.api.enums.ContentType;
import io.github.wrqtec.content.safe.api.enums.RiskSuggestion;
import io.github.wrqtec.content.safe.api.exception.InvalidRequestException;
import io.github.wrqtec.content.safe.api.exception.ProviderCallException;
import io.github.wrqtec.content.safe.api.exception.UnsupportedContentException;
import io.github.wrqtec.content.safe.api.exception.UnsupportedFeatureException;
import io.github.wrqtec.content.safe.model.AsyncTaskRef;
import io.github.wrqtec.content.safe.provider.baidu.BaiduAuditOperationProvider;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link BaiduVideoAsyncOperation} 单元测试：能力位、提交/拉取装配、
 * 处理中与终态映射、脱敏红线、回调受理与拒收
 *
 * @author L.David
 */
class BaiduVideoAsyncOperationTest {

    private static final Gson GSON = new Gson();

    private static final String CONTRACT_DIR = "/contracts/baidu/";

    private final BaiduCensorClient client = mock(BaiduCensorClient.class);
    private final BaiduVideoAsyncOperation operation = new BaiduVideoAsyncOperation(
            BaiduAuditOperationProvider.videoPlan("303"), null, null, client,
            new BaiduAuditResultNormalizer());

    @Test
    void declaresVideoUrlAsyncQueryCapabilities() {
        assertThat(operation.capabilities().contentTypes()).containsExactly(ContentType.VIDEO);
        assertThat(operation.capabilities().url()).isTrue();
        assertThat(operation.capabilities().sync()).isFalse();
        assertThat(operation.capabilities().asyncQuery()).isTrue();
        assertThat(operation.capabilities().asyncCallback()).isFalse();
    }

    @Test
    void callbackCapabilityFollowsConfiguration() {
        BaiduVideoAsyncOperation withCallback = new BaiduVideoAsyncOperation(
                BaiduAuditOperationProvider.videoPlan("303"), "https://cb.example.com/notice", null,
                client, new BaiduAuditResultNormalizer());

        assertThat(withCallback.capabilities().asyncCallback()).isTrue();
    }

    @Test
    void auditIsNotSupported() {
        assertThatThrownBy(() -> operation.audit(videoRequest()))
                .isInstanceOf(UnsupportedFeatureException.class)
                .hasMessageContaining("asynchronous only");
    }

    @Test
    void submitAsyncRejectsNonUrlContent() {
        AuditRequest request = AuditRequest.builder()
                .scene("short_video").type(ContentType.VIDEO)
                .content(ContentRef.base64("aGVsbG8=")).dataId("data-req")
                .build();

        assertThatThrownBy(() -> operation.submitAsync(request))
                .isInstanceOf(UnsupportedContentException.class)
                .hasMessageContaining("url only");
    }

    @Test
    void submitAsyncAssemblesParamsAndReturnsRef() {
        BaiduVideoSubmitResponse response = new BaiduVideoSubmitResponse();
        response.setRet("0");
        response.setMsg("success");
        BaiduVideoSubmitResponse.Data data = new BaiduVideoSubmitResponse.Data();
        data.setTaskId(900001L);
        data.setQueueSize(2);
        response.setData(data);
        when(client.videoSubmit(anyString(),
                anyString(),
                any(),
                any(),
                any())).thenReturn(response);

        AsyncTaskRef ref = operation.submitAsync(videoRequest());

        ArgumentCaptor<String> url = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> extId = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> strategyId = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> noticeUrl = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> subEvents = ArgumentCaptor.forClass(String.class);
        verify(client).videoSubmit(url.capture(), extId.capture(), strategyId.capture(),
                noticeUrl.capture(), subEvents.capture());
        assertThat(url.getValue()).isEqualTo("https://media.example.com/demo.mp4");
        assertThat(extId.getValue()).isEqualTo("data-req");
        assertThat(strategyId.getValue()).isEqualTo("303");
        assertThat(noticeUrl.getValue()).isNull();
        assertThat(subEvents.getValue()).isNull();
        assertThat(ref.providerTaskId()).isEqualTo("900001");
        assertThat(ref.dataId()).isEqualTo("data-req");
    }

    @Test
    void submitAsyncFailsOnBadRet() {
        // ret 断言在生产实现 HttpBaiduCensorClient 内做（BaiduErrorMapper.assertVideoSuccess）；
        // 本测试桩绕过该层，操作对无 taskId 的异常响应兜底抛 ProviderCallException
        BaiduVideoSubmitResponse response = new BaiduVideoSubmitResponse();
        response.setRet("282005");
        response.setMsg("duplicate request");
        when(client.videoSubmit(anyString(),
                anyString(),
                any(),
                any(),
                any())).thenReturn(response);

        assertThatThrownBy(() -> operation.submitAsync(videoRequest()))
                .isInstanceOf(ProviderCallException.class)
                .hasMessageContaining("no taskId");
    }

    @Test
    void submitAsyncFailsWhenNoTaskId() {
        BaiduVideoSubmitResponse response = new BaiduVideoSubmitResponse();
        response.setRet("0");
        response.setData(null);
        when(client.videoSubmit(anyString(),
                anyString(),
                any(),
                any(),
                any())).thenReturn(response);

        assertThatThrownBy(() -> operation.submitAsync(videoRequest()))
                .isInstanceOf(ProviderCallException.class)
                .hasMessageContaining("no taskId");
    }

    @Test
    void queryInProgressWhenNoConclusion() {
        when(client.videoPull("900001")).thenReturn(load("video-pull-inprogress.json",
                BaiduVideoPullResponse.class));

        AuditResult result = operation.query(new AsyncTaskRef("900001", "data-req"));

        assertThat(result.ok()).isTrue();
        assertThat(result.finalResult()).isFalse();
        assertThat(result.suggestion()).isNull();
        assertThat(result.source()).isEqualTo(AuditSource.ASYNC_QUERY);
        assertThat(result.dataId()).isEqualTo("data-req");
        assertThat(result.raw()).containsEntry("taskId", "900001");
    }

    @Test
    void queryFinishMapsFramesAndAudiosWithoutSensitivePayload() {
        when(client.videoPull("900001")).thenReturn(load("video-pull-reject.json",
                BaiduVideoPullResponse.class));

        AuditResult result = operation.query(new AsyncTaskRef("900001", "data-req"));

        assertThat(result.suggestion()).isEqualTo(RiskSuggestion.REJECT);
        assertThat(result.finalResult()).isTrue();
        assertThat(result.requestId()).isEqualTo("123003");
        assertThat(result.hits()).hasSize(2);

        RiskHit frameHit = result.hits().get(0);
        assertThat(frameHit.labelPath()).isEqualTo("43.430101");
        assertThat(frameHit.confidence()).isEqualTo(0.98D);
        assertThat(frameHit.confidenceScale()).isEqualTo(ConfidenceScale.ZERO_TO_ONE);
        assertThat(frameHit.evidence()).isEqualTo("涉黄命中词zz9900112233");
        assertThat(frameHit.raw()).containsEntry("frameTimeStamp", 12L)
                .containsEntry("datasetName", "默认涉黄库");

        RiskHit audioHit = result.hits().get(1);
        assertThat(audioHit.labelPath()).isEqualTo("24");
        assertThat(audioHit.raw()).containsEntry("startTime", 5000)
                .containsEntry("endTime", 9000);
        assertThat(audioHit.confidence()).isEqualTo(0.88D);

        String flattened = String.valueOf(result);
        assertThat(flattened)
                .doesNotContain("bos-sign.example.com")
                .doesNotContain("这段语音转写原文不应透传到统一结果");
    }

    @Test
    void queryFailsOnConclusionTypeError() {
        BaiduVideoPullResponse response = new BaiduVideoPullResponse();
        response.setRet("0");
        BaiduVideoPullResponse.Data data = new BaiduVideoPullResponse.Data();
        data.setTaskId(900001L);
        data.setConclusionType(4);
        data.setErrorMsg("视频解码失败 secret-token-abcdefgh12345678");
        response.setData(data);
        when(client.videoPull("900001")).thenReturn(response);

        assertThatThrownBy(() -> operation.query(new AsyncTaskRef("900001", "data-req")))
                .isInstanceOf(ProviderCallException.class)
                .hasMessageContaining("task failed")
                .hasMessageNotContaining("secret-token-abcdefgh12345678");
    }

    @Test
    void queryRequiresProviderTaskId() {
        assertThatThrownBy(() -> operation.query(new AsyncTaskRef(" ", "data-req")))
                .isInstanceOf(InvalidRequestException.class);
    }

    @Test
    void handleCallbackAcceptsFinishPayload() {
        BaiduVideoCallbackPayload payload = load("video-callback.json", BaiduVideoCallbackPayload.class);
        CallbackResult result = operation.handleCallback(
                new CallbackRequest("baidu", Map.of(), GSON.toJson(payload)));

        assertThat(result.accepted()).isTrue();
        assertThat(result.taskId()).isEqualTo("900001");
        assertThat(result.result().suggestion()).isEqualTo(RiskSuggestion.REJECT);
        assertThat(result.result().source()).isEqualTo(AuditSource.ASYNC_CALLBACK);
        assertThat(result.result().dataId()).isEqualTo("data-cb-001");
        assertThat(String.valueOf(result)).doesNotContain("bos-sign.example.com");
    }

    @Test
    void handleCallbackFallsBackToExtIdWhenTaskIdMissing() {
        CallbackResult result = operation.handleCallback(new CallbackRequest("baidu",
                Map.of(),
                "{\"extId\":\"data-cb-001\",\"result\":{\"conclusion\":\"合规\",\"conclusionType\":1}}"));

        assertThat(result.accepted()).isTrue();
        assertThat(result.taskId()).isEqualTo("data-cb-001");
        assertThat(result.result().suggestion()).isEqualTo(RiskSuggestion.PASS);
    }

    @Test
    void handleCallbackRejectsInvalidAndNonTerminalPayloads() {
        assertThat(operation.handleCallback(
                        new CallbackRequest("baidu", Map.of(), "not-json")).accepted()).isFalse();
        assertThat(operation.handleCallback(
                        new CallbackRequest("baidu", Map.of(),
                                "{\"taskId\":900001}")).result()).isNull();
        assertThat(operation.handleCallback(new CallbackRequest("baidu", Map.of(),
                        "{\"taskId\":900001,\"result\":{\"conclusionType\":null}}")).message())
                .contains("not terminal");
        assertThat(operation.handleCallback(new CallbackRequest("baidu", Map.of(),
                        "{\"result\":{\"conclusionType\":1}}")).message())
                .contains("missing taskId/extId");
    }

    @Test
    void handleCallbackRejectsFailedTaskResult() {
        CallbackResult result = operation.handleCallback(new CallbackRequest("baidu", Map.of(),
                "{\"taskId\":900001,\"result\":{\"errorCode\":282002,\"errorMsg\":\"编码失败\","
                        + "\"conclusionType\":4}}"));

        assertThat(result.accepted()).isFalse();
        assertThat(result.message()).contains("task failed").contains("282002");
    }

    private static AuditRequest videoRequest() {
        return AuditRequest.builder()
                .scene("short_video").type(ContentType.VIDEO)
                .content(ContentRef.url("https://media.example.com/demo.mp4")).dataId("data-req")
                .build();
    }

    private static <T> T load(String name, Class<T> type) {
        try (Reader reader = new InputStreamReader(
                BaiduVideoAsyncOperationTest.class.getResourceAsStream(CONTRACT_DIR + name),
                StandardCharsets.UTF_8)) {
            return GSON.fromJson(reader, type);
        } catch (IOException e) {
            throw new UncheckedIOException("load contract sample failed: " + name, e);
        }
    }
}
