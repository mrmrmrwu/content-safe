package io.github.wrqtec.content.safe.provider.baidu.internal;

import java.util.Base64;

import io.github.wrqtec.content.safe.api.AuditRequest;
import io.github.wrqtec.content.safe.api.AuditResult;
import io.github.wrqtec.content.safe.api.ContentRef;
import io.github.wrqtec.content.safe.api.enums.ContentType;
import io.github.wrqtec.content.safe.api.enums.RiskSuggestion;
import io.github.wrqtec.content.safe.api.exception.UnsupportedContentException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link BaiduImageCensorOperation} 单元测试
 *
 * @author L.David
 */
class BaiduImageCensorOperationTest {

    private final BaiduTextCensorOperationTest.StubClient client = new BaiduTextCensorOperationTest.StubClient();
    private final BaiduImageCensorOperation operation = new BaiduImageCensorOperation(
            BaiduProviderPlanFixture.imagePlan("202"), client, new BaiduAuditResultNormalizer());

    @Test
    void declaresImageCapabilitiesWithAllInputForms() {
        assertThat(operation.capabilities().contentTypes()).containsExactly(ContentType.IMAGE);
        assertThat(operation.capabilities().sync()).isTrue();
        assertThat(operation.capabilities().url()).isTrue();
        assertThat(operation.capabilities().base64()).isTrue();
        assertThat(operation.capabilities().bytes()).isTrue();
    }

    @Test
    void urlFormCallsImageCensorByUrl() {
        client.respond("{\"log_id\":10,\"conclusionType\":1}");

        AuditResult result = operation.audit(
                AuditRequest.image("avatar", ContentRef.url("https://example.com/a.jpg")));

        assertThat(client.calls).hasSize(1);
        assertThat(client.calls.get(0)[0]).isEqualTo("url:https://example.com/a.jpg");
        assertThat(client.calls.get(0)[1]).isEqualTo("202");
        assertThat(result.suggestion()).isEqualTo(RiskSuggestion.PASS);
    }

    @Test
    void base64FormCallsImageCensorByBase64() {
        client.respond("{\"log_id\":11,\"conclusionType\":2}");

        AuditResult result = operation.audit(
                AuditRequest.image("avatar", ContentRef.base64("aGVsbG8=")));

        assertThat(client.calls.get(0)[0]).isEqualTo("base64:aGVsbG8=");
        assertThat(result.suggestion()).isEqualTo(RiskSuggestion.REJECT);
    }

    @Test
    void bytesFormEncodesToBase64() {
        client.respond("{\"log_id\":12,\"conclusionType\":1}");
        byte[] bytes = "hello".getBytes();

        AuditResult result = operation.audit(
                AuditRequest.image("avatar", ContentRef.bytes(bytes)));

        assertThat(client.calls.get(0)[0]).isEqualTo("base64:" + Base64.getEncoder().encodeToString(bytes));
        assertThat(result.ok()).isTrue();
    }

    @Test
    void rejectsTextContent() {
        assertThatThrownBy(() -> operation.audit(AuditRequest.text("comment", "text")))
                .isInstanceOf(UnsupportedContentException.class);
        assertThat(client.calls).isEmpty();
    }

    @Test
    void rejectsOversizeBase64() {
        String hugeBase64 = "a".repeat((int) BaiduLimits.maxImageBytes() + 1);

        assertThatThrownBy(() -> operation.audit(
                AuditRequest.image("avatar", ContentRef.base64(hugeBase64))))
                .isInstanceOf(UnsupportedContentException.class);
        assertThat(client.calls).isEmpty();
    }

    @Test
    void rejectsOversizeBytes() {
        // 原始字节膨胀 4/3 后超过 4MB 上限
        byte[] huge = new byte[(int) BaiduLimits.maxImageBytes()];

        assertThatThrownBy(() -> operation.audit(
                AuditRequest.image("avatar", ContentRef.bytes(huge))))
                .isInstanceOf(UnsupportedContentException.class);
        assertThat(client.calls).isEmpty();
    }

    @Test
    void urlFormSkipsSizeCheck() {
        client.respond("{\"log_id\":13,\"conclusionType\":1}");

        AuditResult result = operation.audit(
                AuditRequest.image("avatar", ContentRef.url("https://example.com/huge.jpg")));

        assertThat(result.ok()).isTrue();
    }
}
