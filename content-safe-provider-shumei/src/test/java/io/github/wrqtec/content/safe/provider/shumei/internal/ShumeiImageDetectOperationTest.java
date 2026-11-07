package io.github.wrqtec.content.safe.provider.shumei.internal;

import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;

import com.google.gson.Gson;
import io.github.wrqtec.content.safe.api.AuditContext;
import io.github.wrqtec.content.safe.api.AuditRequest;
import io.github.wrqtec.content.safe.api.ContentRef;
import io.github.wrqtec.content.safe.api.enums.ContentType;
import io.github.wrqtec.content.safe.api.enums.Severity;
import io.github.wrqtec.content.safe.api.exception.ConfigInvalidException;
import io.github.wrqtec.content.safe.api.exception.ProviderCallException;
import io.github.wrqtec.content.safe.api.exception.UnsupportedContentException;
import io.github.wrqtec.content.safe.model.SceneDefinition;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link ShumeiImageDetectOperation} 单元测试
 *
 * @author L.David
 */
class ShumeiImageDetectOperationTest {

    private static final Gson GSON = new Gson();

    private final StubClient client = new StubClient();
    private final ShumeiImageDetectOperation operation = new ShumeiImageDetectOperation(
            ShumeiProviderPlanFixture.imagePlan("image"), client,
            new ShumeiAuditResultNormalizer(), "POLITY_EROTIC_VIOLENT_QRCODE_ADVERT_IMGTEXTRISK", null);

    @Test
    void declaresImageSyncCapabilities() {
        assertThat(operation.plan().planKey()).isEqualTo("shumei:ImageDetect");
        assertThat(operation.capabilities().contentTypes()).containsExactly(ContentType.IMAGE);
        assertThat(operation.capabilities().sync()).isTrue();
        assertThat(operation.capabilities().asyncQuery()).isFalse();
        assertThat(operation.capabilities().asyncCallback()).isFalse();
    }

    @Test
    void urlContentPassedThrough() {
        client.respond("{\"code\":1100,\"riskLevel\":\"PASS\",\"allLabels\":[]}");

        operation.audit(AuditRequest.image("avatar", ContentRef.url("https://example.com/a.jpg")));

        assertThat(client.calls.get(0).eventId()).isEqualTo("image");
        assertThat(client.calls.get(0).data().get("img")).isEqualTo("https://example.com/a.jpg");
    }

    @Test
    void base64ContentPassedThrough() {
        client.respond("{\"code\":1100,\"riskLevel\":\"PASS\",\"allLabels\":[]}");

        operation.audit(AuditRequest.image("avatar", ContentRef.base64("aGVsbG8=")));

        assertThat(client.calls.get(0).data().get("img")).isEqualTo("aGVsbG8=");
    }

    @Test
    void bytesContentEncodedToBase64() {
        client.respond("{\"code\":1100,\"riskLevel\":\"PASS\",\"allLabels\":[]}");
        byte[] bytes = "hello".getBytes(java.nio.charset.StandardCharsets.UTF_8);

        operation.audit(AuditRequest.image("avatar", ContentRef.bytes(bytes)));

        assertThat(client.calls.get(0).data().get("img"))
                .isEqualTo(Base64.getEncoder().encodeToString(bytes));
    }

    @Test
    void rejectsNonImageContent() {
        AuditRequest request = AuditRequest.text("comment", "文字");

        assertThatThrownBy(() -> operation.audit(request))
                .isInstanceOf(UnsupportedContentException.class);
        assertThat(client.calls).isEmpty();
    }

    @Test
    void sceneExtMergedIntoData() {
        client.respond("{\"code\":1100,\"riskLevel\":\"PASS\",\"allLabels\":[]}");
        SceneDefinition scene = new SceneDefinition("avatar", ContentType.IMAGE,
                null, Severity.LOW, Map.of("interval", "1", "maxFrame", "3"));
        ShumeiImageDetectOperation withScene = new ShumeiImageDetectOperation(
                ShumeiProviderPlanFixture.imagePlan("image"), client,
                new ShumeiAuditResultNormalizer(), "POLITY", scene);

        withScene.audit(AuditRequest.image("avatar", ContentRef.url("https://example.com/a.jpg")));

        assertThat(client.calls.get(0).data())
                .containsEntry("interval", "1")
                .containsEntry("maxFrame", "3");
    }

    @Test
    void sceneExtCollidingWithReservedKeyFailsFast() {
        SceneDefinition scene = new SceneDefinition("avatar", ContentType.IMAGE,
                null, Severity.LOW, Map.of("img", "https://evil.example.com/x.jpg"));
        ShumeiImageDetectOperation withScene = new ShumeiImageDetectOperation(
                ShumeiProviderPlanFixture.imagePlan("image"), client,
                new ShumeiAuditResultNormalizer(), "POLITY", scene);

        assertThatThrownBy(() -> withScene.audit(
                AuditRequest.image("avatar", ContentRef.url("https://example.com/a.jpg"))))
                .isInstanceOf(ConfigInvalidException.class)
                .hasMessageContaining("img");
        assertThat(client.calls).isEmpty();
    }

    @Test
    void tokenIdAndDataIdPropagated() {
        client.respond("{\"code\":1100,\"requestId\":\"r-9\",\"riskLevel\":\"PASS\",\"allLabels\":[]}");

        AuditRequest request = AuditRequest.builder()
                .scene("avatar").type(ContentType.IMAGE)
                .content(ContentRef.url("https://example.com/a.jpg"))
                .dataId("d-7")
                .context(new AuditContext("u-1", null, null, null))
                .build();
        operation.audit(request);

        assertThat(client.calls.get(0).data()).containsEntry("dataId", "d-7");
        assertThat(client.calls.get(0).data()).containsEntry("tokenId", "u-1");
    }

    @Test
    void mapsClientException() {
        client.failWith(new RuntimeException("image fetch timeout"));

        assertThatThrownBy(() -> operation.audit(
                AuditRequest.image("avatar", ContentRef.url("https://example.com/a.jpg"))))
                .isInstanceOf(ProviderCallException.class)
                .hasMessageContaining("RuntimeException");
    }

    /**
     * 可编程客户端桩（仅记录 image 调用）
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
            throw new UnsupportedOperationException();
        }

        @Override
        public ShumeiSyncResponse imageDetect(String eventId, String type, Map<String, Object> data) {
            calls.add(new Call(eventId, type, data));
            if (failure != null) {
                throw failure;
            }
            return GSON.fromJson(responseJson, ShumeiSyncResponse.class);
        }

        @Override
        public ShumeiSyncResponse imageAsyncSubmit(String eventId, String type, Map<String, Object> data) {
            throw new UnsupportedOperationException();
        }

        @Override
        public ShumeiAsyncQueryResponse imageAsyncQuery(List<String> requestIds) {
            throw new UnsupportedOperationException();
        }
    }
}
