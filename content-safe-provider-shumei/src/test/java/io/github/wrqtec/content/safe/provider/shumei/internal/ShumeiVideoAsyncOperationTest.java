package io.github.wrqtec.content.safe.provider.shumei.internal;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.google.gson.Gson;
import io.github.wrqtec.content.safe.api.AuditContext;
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
import io.github.wrqtec.content.safe.api.util.RiskHitCaps;
import io.github.wrqtec.content.safe.model.AsyncTaskRef;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link ShumeiVideoAsyncOperation} 单元测试
 *
 * @author L.David
 */
class ShumeiVideoAsyncOperationTest {

    private static final Gson GSON = new Gson();

    private final VideoStubClient client = new VideoStubClient();
    private final ShumeiVideoAsyncOperation operation = new ShumeiVideoAsyncOperation(
            ShumeiProviderPlanFixture.videoPlan("video"), client,
            new ShumeiAuditResultNormalizer(),
            "POLITY_EROTIC_VIOLENT_QRCODE_ADVERT_IMGTEXTRISK",
            "POLITY_EROTIC_ADVERT_BAN_VIOLENT_DIRTY_ADLAW", 0, null);

    @Test
    void declaresVideoCapabilities() {
        assertThat(operation.plan().planKey()).isEqualTo("shumei:VideoSubmit");
        assertThat(operation.plan().mode().name()).isEqualTo("ASYNC_QUERY");
        assertThat(operation.capabilities().contentTypes()).containsExactly(ContentType.VIDEO);
        assertThat(operation.capabilities().sync()).isFalse();
        assertThat(operation.capabilities().url()).isTrue();
        assertThat(operation.capabilities().base64()).isFalse();
        assertThat(operation.capabilities().bytes()).isFalse();
        assertThat(operation.capabilities().asyncQuery()).isTrue();
        assertThat(operation.capabilities().asyncCallback()).isTrue();
    }

    @Test
    void syncAuditNotSupported() {
        assertThatThrownBy(() -> operation.audit(AuditRequest.video("short_video",
                ContentRef.url("https://example.com/v.mp4"))))
                .isInstanceOf(UnsupportedFeatureException.class);
        assertThat(client.submitCalls).isEmpty();
    }

    @Test
    void submitAsyncCarriesTaskReferenceAndParams() {
        client.respondSubmit(load("video-submit.json"));

        AuditRequest request = AuditRequest.builder()
                .scene("short_video").type(ContentType.VIDEO)
                .content(ContentRef.url("https://example.com/v.mp4"))
                .dataId("d-7")
                .context(new AuditContext("u-1", null, "1.2.3.4", Map.of()))
                .build();
        AsyncTaskRef ref = operation.submitAsync(request);

        assertThat(ref.providerTaskId()).isEqualTo("d-7");
        assertThat(ref.dataId()).isEqualTo("d-7");
        assertThat(client.submitCalls.get(0).eventId()).isEqualTo("video");
        assertThat(client.submitCalls.get(0).imgType())
                .isEqualTo("POLITY_EROTIC_VIOLENT_QRCODE_ADVERT_IMGTEXTRISK");
        assertThat(client.submitCalls.get(0).audioType())
                .isEqualTo("POLITY_EROTIC_ADVERT_BAN_VIOLENT_DIRTY_ADLAW");
        assertThat(client.submitCalls.get(0).data())
                .containsEntry("url", "https://example.com/v.mp4")
                .containsEntry("btId", "d-7")
                .containsEntry("screenshotType", 0)
                .containsEntry("ip", "1.2.3.4");
    }

    @Test
    void submitAsyncGeneratesBtIdWhenDataIdMissing() {
        client.respondSubmit(load("video-submit.json"));

        AsyncTaskRef ref = operation.submitAsync(AuditRequest.video("short_video",
                ContentRef.url("https://example.com/v.mp4")));

        assertThat(ref.providerTaskId()).hasSize(32).doesNotContain("-");
        assertThat(ref.dataId()).isEqualTo(ref.providerTaskId());
    }

    @Test
    void submitAsyncRejectsNonVideoContent() {
        assertThatThrownBy(() -> operation.submitAsync(AuditRequest.text("comment", "文字")))
                .isInstanceOf(UnsupportedContentException.class)
                .hasMessageContaining("url content only");
        assertThat(client.submitCalls).isEmpty();
    }

    @Test
    void submitAsyncRejectsNonUrlContent() {
        assertThatThrownBy(() -> operation.submitAsync(AuditRequest.video("short_video",
                ContentRef.base64("aGVsbG8="))))
                .isInstanceOf(UnsupportedContentException.class)
                .hasMessageContaining("url content only");
        assertThat(client.submitCalls).isEmpty();
    }

    @Test
    void queryProcessingReturnsIntermediateState() {
        client.respondQuery(load("video-query-inprogress.json"));

        AuditResult result = operation.query(new AsyncTaskRef("data-req-1", "data-req-1"));

        assertThat(result.ok()).isTrue();
        assertThat(result.finalResult()).isFalse();
        assertThat(result.suggestion()).isNull();
        assertThat(result.source()).isEqualTo(AuditSource.ASYNC_QUERY);
        assertThat(result.requestId()).isEqualTo("data-req-1");
    }

    @Test
    void queryNormalizesFrameAndAudioHitsWithoutSensitivePayload() {
        client.respondQuery(load("video-query-reject.json"));

        AuditResult result = operation.query(new AsyncTaskRef("data-req-1", "data-req-1"));

        assertThat(result.source()).isEqualTo(AuditSource.ASYNC_QUERY);
        assertThat(result.finalResult()).isTrue();
        assertThat(result.suggestion()).isEqualTo(RiskSuggestion.REJECT);
        assertThat(result.requestId()).isEqualTo("req-video-query-001");

        List<RiskHit> hits = result.hits();
        assertThat(hits).hasSize(2);

        RiskHit frameHit = hits.get(0);
        assertThat(frameHit.labelPath()).isEqualTo("polity/politician");
        assertThat(frameHit.confidence()).isEqualTo(0.97D);
        assertThat(frameHit.confidenceScale()).isEqualTo(ConfidenceScale.ZERO_TO_ONE);
        assertThat(frameHit.raw()).containsEntry("kind", "frame").containsEntry("riskSource", 1002);
        assertThat(frameHit.raw()).containsKey("matchedWords");

        RiskHit audioHit = hits.get(1);
        assertThat(audioHit.labelPath()).isEqualTo("ad");
        assertThat(audioHit.confidence()).isEqualTo(0.88D);
        assertThat(audioHit.raw()).containsEntry("kind", "audio").containsEntry("riskSource", 1001);

        // 截帧/语音签名 URL 与语音转写原文不透传（命中关键词是映射设计内透传，不属泄漏）
        String flattened = String.valueOf(result);
        assertThat(flattened).doesNotContain("signed.example.com");
        assertThat(flattened).doesNotContain("语音转写原文不应透传");
    }

    @Test
    void queryRequiresTaskReference() {
        assertThatThrownBy(() -> operation.query(new AsyncTaskRef(" ", "d-7")))
                .isInstanceOf(InvalidRequestException.class);
        assertThat(client.queryCalls).isEmpty();
    }

    @Test
    void queryFailsOnQueryErrorCode() {
        client.respondQuery("{\"code\":1903,\"message\":\"service error\"}");

        assertThatThrownBy(() -> operation.query(new AsyncTaskRef("data-req-1", "d-7")))
                .isInstanceOf(ProviderCallException.class)
                .hasMessageContaining("video query rejected")
                .hasMessageContaining("1903");
    }

    @Test
    void handleCallbackAcceptsFinalPayload() {
        CallbackResult result = operation.handleCallback(new CallbackRequest("shumei", Map.of(),
                load("video-callback.json")));

        assertThat(result.accepted()).isTrue();
        assertThat(result.taskId()).isEqualTo("data-cb-001");
        assertThat(result.result().source()).isEqualTo(AuditSource.ASYNC_CALLBACK);
        assertThat(result.result().suggestion()).isEqualTo(RiskSuggestion.REJECT);
        assertThat(result.result().dataId()).isEqualTo("data-cb-001");
        assertThat(result.result().hits()).hasSize(1);
        assertThat(String.valueOf(result.result())).doesNotContain("signed.example.com");
    }

    @Test
    void handleCallbackRejectsInvalidJson() {
        CallbackResult result = operation.handleCallback(new CallbackRequest("shumei", Map.of(),
                "{not-json"));

        assertThat(result.accepted()).isFalse();
        assertThat(result.message()).contains("invalid shumei video callback payload");
    }

    @Test
    void handleCallbackRejectsBusinessError() {
        CallbackResult result = operation.handleCallback(new CallbackRequest("shumei", Map.of(),
                "{\"code\":1903,\"message\":\"service error\"}"));

        assertThat(result.accepted()).isFalse();
        assertThat(result.message()).contains("1903");
    }

    @Test
    void handleCallbackRejectsMissingBtId() {
        CallbackResult result = operation.handleCallback(new CallbackRequest("shumei", Map.of(),
                "{\"code\":1100,\"riskLevel\":\"PASS\"}"));

        assertThat(result.accepted()).isFalse();
        assertThat(result.message()).contains("btId");
    }

    private static String load(String fileName) {
        try (InputStream in = ShumeiVideoAsyncOperationTest.class
                .getResourceAsStream("/contracts/shumei/" + fileName)) {
            if (in == null) {
                throw new IllegalStateException("contract not found: " + fileName);
            }
            try (InputStreamReader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
                StringBuilder sb = new StringBuilder();
                int c;
                while ((c = reader.read()) != -1) {
                    sb.append((char) c);
                }
                return sb.toString();
            }
        } catch (IOException e) {
            throw new IllegalStateException("contract load failed: " + fileName, e);
        }
    }

    @Test
    void queryCapsVideoHitsWithTruncationFlags() {
        StringBuilder frameDetails = new StringBuilder();
        for (int i = 0; i < 60; i++) {
            if (i > 0) {
                frameDetails.append(',');
            }
            frameDetails.append("{\"riskLevel\":\"REJECT\",\"riskLabel1\":\"polity\",\"riskLabel2\":\"l")
                    .append(i).append("\",\"allLabels\":[{\"probability\":0.9,\"riskLevel\":\"REJECT\","
                            + "\"riskLabel1\":\"polity\",\"riskLabel2\":\"l").append(i).append("\"}]}");
        }
        client.respondQuery("{\"code\":1100,\"message\":\"success\",\"requestId\":\"req-cap\","
                + "\"btId\":\"data-req-1\",\"riskLevel\":\"REJECT\","
                + "\"auxInfo\":{\"frameDetail\":[" + frameDetails + "]}}");

        AuditResult result = operation.query(new AsyncTaskRef("data-req-1", "data-req-1"));

        assertThat(result.hits()).hasSize(RiskHitCaps.DEFAULT_VIDEO_LIMIT);
        assertThat(result.raw().get("truncated")).isEqualTo(Boolean.TRUE);
        assertThat(result.raw().get("totalHits")).isEqualTo(60);
    }

    /**
     * 视频客户端桩：提交回执与查询响应可编程
     */
    static class VideoStubClient implements ShumeiClientInvoker {

        record VideoSubmitCall(String eventId, String imgType, String audioType, Map<String, Object> data) {
        }

        final List<VideoSubmitCall> submitCalls = new ArrayList<>();
        final List<String> queryCalls = new ArrayList<>();
        private String submitJson;
        private String queryJson;

        void respondSubmit(String json) {
            this.submitJson = json;
        }

        void respondQuery(String json) {
            this.queryJson = json;
        }

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
        public ShumeiAsyncQueryResponse imageAsyncQuery(List<String> requestIds) {
            throw new UnsupportedOperationException();
        }

        @Override
        public ShumeiVideoQueryResponse videoSubmit(String eventId, String imgType, String audioType,
                                                    Map<String, Object> data) {
            submitCalls.add(new VideoSubmitCall(eventId, imgType, audioType, data));
            return GSON.fromJson(submitJson, ShumeiVideoQueryResponse.class);
        }

        @Override
        public ShumeiVideoQueryResponse videoQuery(String btId) {
            queryCalls.add(btId);
            return GSON.fromJson(queryJson, ShumeiVideoQueryResponse.class);
        }
    }
}
