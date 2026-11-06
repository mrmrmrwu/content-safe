package io.github.wrqtec.content.safe.provider.baidu.internal;

import java.util.ArrayList;
import java.util.List;

import com.google.gson.Gson;
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
 * {@link BaiduTextCensorOperation} 单元测试
 *
 * @author L.David
 */
class BaiduTextCensorOperationTest {

    private static final Gson GSON = new Gson();

    private final StubClient client = new StubClient();
    private final BaiduTextCensorOperation operation = new BaiduTextCensorOperation(
            BaiduProviderPlanFixture.textPlan("101"), client, new BaiduAuditResultNormalizer());

    @Test
    void declaresTextSyncCapabilities() {
        assertThat(operation.plan().planKey()).isEqualTo("baidu:TextCensor");
        assertThat(operation.capabilities().contentTypes()).containsExactly(ContentType.TEXT);
        assertThat(operation.capabilities().sync()).isTrue();
        assertThat(operation.capabilities().asyncQuery()).isFalse();
        assertThat(operation.capabilities().asyncCallback()).isFalse();
    }

    @Test
    void assemblesTextAndStrategyId() {
        client.respond("{\"log_id\":1,\"conclusionType\":1}");

        AuditResult result = operation.audit(AuditRequest.text("comment", "正常文本"));

        assertThat(client.calls).hasSize(1);
        assertThat(client.calls.get(0)[0]).isEqualTo("正常文本");
        assertThat(client.calls.get(0)[1]).isEqualTo("101");
        assertThat(result.suggestion()).isEqualTo(RiskSuggestion.PASS);
        assertThat(result.provider()).isEqualTo("baidu");
    }

    @Test
    void rejectsNonTextContent() {
        AuditRequest request = AuditRequest.builder()
                .scene("avatar")
                .type(io.github.wrqtec.content.safe.api.enums.ContentType.IMAGE)
                .content(io.github.wrqtec.content.safe.api.ContentRef.url("https://example.com/a.jpg"))
                .build();

        assertThatThrownBy(() -> operation.audit(request))
                .isInstanceOf(UnsupportedContentException.class);
        assertThat(client.calls).isEmpty();
    }

    @Test
    void rejectsOversizeContent() {
        // 20001 个 ASCII 字符 = 20001 字节 > 20000 上限
        String huge = "a".repeat(20_001);

        assertThatThrownBy(() -> operation.audit(AuditRequest.text("comment", huge)))
                .isInstanceOf(UnsupportedContentException.class)
                .hasMessageContaining("20001");
        assertThat(client.calls).isEmpty();
    }

    @Test
    void acceptsExactlyMaxSizeContent() {
        client.respond("{\"log_id\":2,\"conclusionType\":1}");

        AuditResult result = operation.audit(AuditRequest.text("comment", "a".repeat(20_000)));

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
        ProviderCallException original = new ProviderCallException("baidu censor rejected: error_code=18");
        client.failWith(original);

        assertThatThrownBy(() -> operation.audit(AuditRequest.text("comment", "text")))
                .isSameAs(original);
    }

    /**
     * 可编程客户端桩
     */
    static class StubClient implements BaiduCensorClient {

        final List<String[]> calls = new ArrayList<>();
        private String responseJson;
        private RuntimeException failure;

        void respond(String json) {
            this.responseJson = json;
        }

        void failWith(RuntimeException e) {
            this.failure = e;
        }

        @Override
        public BaiduCensorResponse textCensor(String text, String strategyId) {
            calls.add(new String[]{text, strategyId});
            return doRespond();
        }

        @Override
        public BaiduCensorResponse imageCensorByBase64(String base64, String strategyId) {
            calls.add(new String[]{"base64:" + base64, strategyId});
            return doRespond();
        }

        @Override
        public BaiduCensorResponse imageCensorByUrl(String imgUrl, String strategyId) {
            calls.add(new String[]{"url:" + imgUrl, strategyId});
            return doRespond();
        }

        private BaiduCensorResponse doRespond() {
            if (failure != null) {
                throw failure;
            }
            return GSON.fromJson(responseJson, BaiduCensorResponse.class);
        }
    }
}
