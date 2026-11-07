package io.github.wrqtec.content.safe.provider.shumei.internal;

import java.util.Map;

import io.github.wrqtec.content.safe.api.CallbackRequest;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link ShumeiCallbackVerifier} 单元测试
 *
 * @author L.David
 */
class ShumeiCallbackVerifierTest {

    private final ShumeiCallbackVerifier verifier = new ShumeiCallbackVerifier();

    @Test
    void providerIsShumei() {
        assertThat(verifier.provider()).isEqualTo("shumei");
    }

    @Test
    void acceptsValidPayload() {
        CallbackRequest request = new CallbackRequest("shumei", Map.of(),
                "{\"code\":1100,\"requestId\":\"req-1\",\"riskLevel\":\"PASS\"}");

        assertThat(verifier.verify(request, null, Map.of())).isTrue();
    }

    @Test
    void acceptsPayloadWithoutRiskFields() {
        // 异步提交回显形态的报文（无 riskLevel）同样结构合法
        CallbackRequest request = new CallbackRequest("shumei", Map.of(),
                "{\"code\":1100,\"requestId\":\"req-1\"}");

        assertThat(verifier.verify(request, null, Map.of())).isTrue();
    }

    @Test
    void rejectsInvalidJson() {
        CallbackRequest request = new CallbackRequest("shumei", Map.of(), "{not-json");

        assertThat(verifier.verify(request, null, Map.of())).isFalse();
    }

    @Test
    void rejectsNullPayload() {
        assertThat(verifier.verify(new CallbackRequest("shumei", Map.of(), "   "), null, Map.of()))
                .isFalse();
    }

    @Test
    void rejectsNullRequest() {
        assertThat(verifier.verify(null, null, Map.of())).isFalse();
    }
}
