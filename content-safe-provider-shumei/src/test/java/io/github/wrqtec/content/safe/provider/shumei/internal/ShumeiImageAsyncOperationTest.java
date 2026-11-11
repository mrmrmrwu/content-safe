package io.github.wrqtec.content.safe.provider.shumei.internal;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.google.gson.Gson;
import io.github.wrqtec.content.safe.api.AuditRequest;
import io.github.wrqtec.content.safe.api.AuditResult;
import io.github.wrqtec.content.safe.api.CallbackRequest;
import io.github.wrqtec.content.safe.api.CallbackResult;
import io.github.wrqtec.content.safe.api.ContentRef;
import io.github.wrqtec.content.safe.api.enums.AuditSource;
import io.github.wrqtec.content.safe.api.enums.ContentType;
import io.github.wrqtec.content.safe.api.enums.RiskSuggestion;
import io.github.wrqtec.content.safe.api.exception.InvalidRequestException;
import io.github.wrqtec.content.safe.api.exception.ProviderCallException;
import io.github.wrqtec.content.safe.api.exception.UnsupportedContentException;
import io.github.wrqtec.content.safe.api.exception.UnsupportedFeatureException;
import io.github.wrqtec.content.safe.model.AsyncTaskRef;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link ShumeiImageAsyncOperation} 单元测试
 *
 * @author L.David
 */
class ShumeiImageAsyncOperationTest {

    private static final Gson GSON = new Gson();

    private final AsyncStubClient client = new AsyncStubClient();
    private final ShumeiImageAsyncOperation operation = new ShumeiImageAsyncOperation(
            ShumeiProviderPlanFixture.imageAsyncPlan("image"), client,
            new ShumeiAuditResultNormalizer(), "POLITY_EROTIC_VIOLENT_QRCODE_ADVERT_IMGTEXTRISK", null);

    @Test
    void declaresImageAsyncCapabilities() {
        assertThat(operation.plan().planKey()).isEqualTo("shumei:ImageAsyncSubmit");
        assertThat(operation.plan().mode().name()).isEqualTo("ASYNC_QUERY");
        assertThat(operation.capabilities().contentTypes()).containsExactly(ContentType.IMAGE);
        assertThat(operation.capabilities().sync()).isFalse();
        assertThat(operation.capabilities().asyncQuery()).isTrue();
        assertThat(operation.capabilities().asyncCallback()).isTrue();
        assertThat(operation.capabilities().url()).isTrue();
        assertThat(operation.capabilities().base64()).isTrue();
        assertThat(operation.capabilities().bytes()).isTrue();
    }

    @Test
    void syncAuditNotSupported() {
        assertThatThrownBy(() -> operation.audit(AuditRequest.image("avatar",
                ContentRef.url("https://example.com/a.jpg"))))
                .isInstanceOf(UnsupportedFeatureException.class);
        assertThat(client.submitCalls).isEmpty();
    }

    @Test
    void submitAsyncCarriesTaskReference() {
        client.respondSubmit("{\"code\":1100,\"requestId\":\"req-1\",\"message\":\"ok\"}");

        AuditRequest request = AuditRequest.builder()
                .scene("avatar").type(ContentType.IMAGE)
                .content(ContentRef.url("https://example.com/a.jpg"))
                .dataId("d-7")
                .build();
        AsyncTaskRef ref = operation.submitAsync(request);

        assertThat(ref.providerTaskId()).isEqualTo("req-1");
        assertThat(ref.dataId()).isEqualTo("d-7");
        assertThat(client.submitCalls.get(0).eventId()).isEqualTo("image");
        assertThat(client.submitCalls.get(0).data()).containsEntry("dataId", "d-7");
        assertThat(client.submitCalls.get(0).data()).containsEntry("img", "https://example.com/a.jpg");
    }

    @Test
    void submitAsyncWithoutRequestIdentifierFails() {
        client.respondSubmit("{\"code\":1100,\"message\":\"ok\"}");

        assertThatThrownBy(() -> operation.submitAsync(AuditRequest.image("avatar",
                ContentRef.url("https://example.com/a.jpg"))))
                .isInstanceOf(ProviderCallException.class)
                .hasMessageContaining("requestId");
    }

    @Test
    void submitAsyncRejectsNonImageContent() {
        assertThatThrownBy(() -> operation.submitAsync(AuditRequest.text("comment", "文字")))
                .isInstanceOf(UnsupportedContentException.class);
        assertThat(client.submitCalls).isEmpty();
    }

    @Test
    void queryNormalizesFinalResult() {
        client.respondQuery("{\"code\":1100,\"contents\":[{"
                + "\"code\":1100,\"requestId\":\"req-1\",\"riskLevel\":\"REJECT\","
                + "\"riskLabel1\":\"polity\",\"finalResult\":1,\"resultType\":1}]}");

        AuditResult result = operation.query(new AsyncTaskRef("req-1", "d-7"));

        assertThat(result.source()).isEqualTo(AuditSource.ASYNC_QUERY);
        assertThat(result.finalResult()).isTrue();
        assertThat(result.suggestion()).isEqualTo(RiskSuggestion.REJECT);
        assertThat(result.dataId()).isEqualTo("d-7");
        assertThat(result.requestId()).isEqualTo("req-1");
        assertThat(result.hits()).isNotEmpty();
    }

    @Test
    void queryWithEmptyContentsReturnsIntermediateState() {
        client.respondQuery("{\"code\":1100,\"contents\":[]}");

        AuditResult result = operation.query(new AsyncTaskRef("req-1", "d-7"));

        assertThat(result.ok()).isTrue();
        assertThat(result.finalResult()).isFalse();
        assertThat(result.suggestion()).isNull();
        assertThat(result.source()).isEqualTo(AuditSource.ASYNC_QUERY);
        assertThat(result.requestId()).isEqualTo("req-1");
    }

    @Test
    void queryRequiresTaskReference() {
        assertThatThrownBy(() -> operation.query(new AsyncTaskRef(" ", "d-7")))
                .isInstanceOf(InvalidRequestException.class);
        assertThat(client.queryCalls).isEmpty();
    }

    @Test
    void handleCallbackAcceptsFinalPayload() {
        CallbackResult result = operation.handleCallback(new CallbackRequest("shumei", Map.of(),
                "{\"code\":1100,\"requestId\":\"req-9\",\"riskLevel\":\"REVIEW\","
                        + "\"riskLabel1\":\"ad\",\"finalResult\":1,\"resultType\":0}"));

        assertThat(result.accepted()).isTrue();
        assertThat(result.taskId()).isEqualTo("req-9");
        assertThat(result.result().source()).isEqualTo(AuditSource.ASYNC_CALLBACK);
        assertThat(result.result().suggestion()).isEqualTo(RiskSuggestion.REVIEW);
    }

    @Test
    void handleCallbackRejectsInvalidJson() {
        CallbackResult result = operation.handleCallback(new CallbackRequest("shumei", Map.of(),
                "{not-json"));

        assertThat(result.accepted()).isFalse();
        assertThat(result.message()).contains("invalid shumei callback payload");
    }

    @Test
    void handleCallbackRejectsBusinessError() {
        CallbackResult result = operation.handleCallback(new CallbackRequest("shumei", Map.of(),
                "{\"code\":1903,\"message\":\"service error\"}"));

        assertThat(result.accepted()).isFalse();
        assertThat(result.message()).contains("1903");
    }

    @Test
    void handleCallbackRejectsMissingRequestIdentifier() {
        CallbackResult result = operation.handleCallback(new CallbackRequest("shumei", Map.of(),
                "{\"code\":1100,\"riskLevel\":\"PASS\"}"));

        assertThat(result.accepted()).isFalse();
        assertThat(result.message()).contains("requestId");
    }

    /**
     * 异步客户端桩：提交响应与查询响应可编程
     */
    static class AsyncStubClient implements ShumeiClientInvoker {

        record SubmitCall(String eventId, String type, Map<String, Object> data) {
        }

        final List<SubmitCall> submitCalls = new ArrayList<>();
        final List<List<String>> queryCalls = new ArrayList<>();
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
            submitCalls.add(new SubmitCall(eventId, type, data));
            return GSON.fromJson(submitJson, ShumeiSyncResponse.class);
        }

        @Override
        public ShumeiAsyncQueryResponse imageAsyncQuery(List<String> requestIds) {
            queryCalls.add(requestIds);
            return GSON.fromJson(queryJson, ShumeiAsyncQueryResponse.class);
        }

        @Override
        public ShumeiVideoQueryResponse videoSubmit(String eventId, String imgType, String audioType,
                                                    Map<String, Object> data) {
            throw new UnsupportedOperationException();
        }

        @Override
        public ShumeiVideoQueryResponse videoQuery(String btId) {
            throw new UnsupportedOperationException();
        }
    }
}
