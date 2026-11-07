package io.github.wrqtec.content.safe.provider.shumei.internal;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.google.gson.Gson;
import io.github.wrqtec.content.safe.api.AuditContext;
import io.github.wrqtec.content.safe.api.AuditRequest;
import io.github.wrqtec.content.safe.api.AuditResult;
import io.github.wrqtec.content.safe.api.enums.ContentType;
import io.github.wrqtec.content.safe.api.enums.RiskSuggestion;
import io.github.wrqtec.content.safe.api.exception.ProviderCallException;
import io.github.wrqtec.content.safe.api.exception.UnsupportedContentException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link ShumeiTextDetectOperation} 单元测试
 *
 * @author L.David
 */
class ShumeiTextDetectOperationTest {

    private static final Gson GSON = new Gson();

    private final StubClient client = new StubClient();
    private final ShumeiTextDetectOperation operation = new ShumeiTextDetectOperation(
            ShumeiProviderPlanFixture.textPlan("text"), client,
            new ShumeiAuditResultNormalizer(), "TEXTRISK", null);

    @Test
    void declaresTextSyncCapabilities() {
        assertThat(operation.plan().planKey()).isEqualTo("shumei:TextDetect");
        assertThat(operation.capabilities().contentTypes()).containsExactly(ContentType.TEXT);
        assertThat(operation.capabilities().sync()).isTrue();
        assertThat(operation.capabilities().asyncQuery()).isFalse();
        assertThat(operation.capabilities().asyncCallback()).isFalse();
    }

    @Test
    void assemblesEventIdTypeAndData() {
        client.respond("{\"code\":1100,\"riskLevel\":\"PASS\",\"allLabels\":[]}");

        AuditResult result = operation.audit(AuditRequest.text("comment", "正常文本"));

        assertThat(client.calls).hasSize(1);
        assertThat(client.calls.get(0).eventId()).isEqualTo("text");
        assertThat(client.calls.get(0).type()).isEqualTo("TEXTRISK");
        assertThat(client.calls.get(0).data().get("text")).isEqualTo("正常文本");
        assertThat(client.calls.get(0).data()).containsKeys("tokenId");
        assertThat(result.suggestion()).isEqualTo(RiskSuggestion.PASS);
        assertThat(result.provider()).isEqualTo("shumei");
    }

    @Test
    void tokenIdPrefersUserIdThenDataId() {
        client.respond("{\"code\":1100,\"riskLevel\":\"PASS\",\"allLabels\":[]}");

        AuditRequest withUser = AuditRequest.builder()
                .scene("comment").type(ContentType.TEXT)
                .content(io.github.wrqtec.content.safe.api.ContentRef.text("hello"))
                .dataId("d-1")
                .context(new AuditContext("u-9", null, null, null))
                .build();
        operation.audit(withUser);
        assertThat(client.calls.get(0).data().get("tokenId")).isEqualTo("u-9");

        AuditRequest withoutUser = AuditRequest.builder()
                .scene("comment").type(ContentType.TEXT)
                .content(io.github.wrqtec.content.safe.api.ContentRef.text("hello"))
                .dataId("d-1")
                .build();
        operation.audit(withoutUser);
        assertThat(client.calls.get(1).data().get("tokenId")).isEqualTo("d-1");
    }

    @Test
    void tokenIdFallsBackToRandomWhenNoUserAndDataId() {
        client.respond("{\"code\":1100,\"riskLevel\":\"PASS\",\"allLabels\":[]}");

        operation.audit(AuditRequest.text("comment", "hello"));

        String tokenId = String.valueOf(client.calls.get(0).data().get("tokenId"));
        assertThat(tokenId).hasSize(32).doesNotContain("-").matches("[0-9a-f]+");
    }

    @Test
    void propagatesIpFromContext() {
        client.respond("{\"code\":1100,\"riskLevel\":\"PASS\",\"allLabels\":[]}");

        AuditRequest request = AuditRequest.builder()
                .scene("comment").type(ContentType.TEXT)
                .content(io.github.wrqtec.content.safe.api.ContentRef.text("hello"))
                .context(new AuditContext("u-1", "dev-1", "10.0.0.1", null))
                .build();
        operation.audit(request);

        assertThat(client.calls.get(0).data()).containsEntry("ip", "10.0.0.1");
    }

    @Test
    void rejectsNonTextContent() {
        AuditRequest request = AuditRequest.builder()
                .scene("avatar")
                .type(ContentType.IMAGE)
                .content(io.github.wrqtec.content.safe.api.ContentRef.url("https://example.com/a.jpg"))
                .build();

        assertThatThrownBy(() -> operation.audit(request))
                .isInstanceOf(UnsupportedContentException.class);
        assertThat(client.calls).isEmpty();
    }

    @Test
    void rejectsOversizeContent() {
        // 10001 个码点 > 官方单次 1 万字上限（多字节字符按码点计数）
        String huge = "汉".repeat(10_001);

        assertThatThrownBy(() -> operation.audit(AuditRequest.text("comment", huge)))
                .isInstanceOf(UnsupportedContentException.class)
                .hasMessageContaining("10001");
        assertThat(client.calls).isEmpty();
    }

    @Test
    void acceptsExactlyMaxChars() {
        client.respond("{\"code\":1100,\"riskLevel\":\"PASS\",\"allLabels\":[]}");

        AuditResult result = operation.audit(AuditRequest.text("comment", "a".repeat(10_000)));

        assertThat(result.ok()).isTrue();
    }

    @Test
    void mapsClientException() {
        client.failWith(new RuntimeException("socket reset"));

        assertThatThrownBy(() -> operation.audit(AuditRequest.text("comment", "text")))
                .isInstanceOf(ProviderCallException.class)
                .hasMessageContaining("RuntimeException");
    }

    @Test
    void propagatesContentSafeExceptionUnwrapped() {
        ProviderCallException original = new ProviderCallException("shumei detect rejected: code=1901");
        client.failWith(original);

        assertThatThrownBy(() -> operation.audit(AuditRequest.text("comment", "text")))
                .isSameAs(original);
    }

    /**
     * 可编程客户端桩
     */
    static class StubClient implements ShumeiClientInvoker {

        record Call(String eventId, String type, Map<String, Object> data) {
        }

        final List<Call> calls = new ArrayList<>();
        private String responseJson;
        private RuntimeException failure;

        void respond(String json) {
            this.responseJson = json;
        }

        void failWith(RuntimeException e) {
            this.failure = e;
        }

        @Override
        public ShumeiSyncResponse textDetect(String eventId, String type, Map<String, Object> data) {
            calls.add(new Call(eventId, type, data));
            return doRespond();
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

        private ShumeiSyncResponse doRespond() {
            if (failure != null) {
                throw failure;
            }
            return GSON.fromJson(responseJson, ShumeiSyncResponse.class);
        }
    }
}
