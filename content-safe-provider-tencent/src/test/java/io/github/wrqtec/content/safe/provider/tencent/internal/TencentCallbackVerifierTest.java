package io.github.wrqtec.content.safe.provider.tencent.internal;

import java.util.Map;

import io.github.wrqtec.content.safe.api.CallbackRequest;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link TencentCallbackVerifier} 单元测试（官方无签名机制，仅结构校验）
 *
 * @author L.David
 */
class TencentCallbackVerifierTest {

    private final TencentCallbackVerifier verifier = new TencentCallbackVerifier();

    @Test
    void declaresTencentProvider() {
        assertThat(verifier.provider()).isEqualTo("tencent");
    }

    @Test
    void acceptsWellFormedPayload() {
        String payload = "{\"DataId\":\"img-1\",\"Suggestion\":\"Block\",\"Label\":\"Porn\"}";
        assertThat(verifier.verify(new CallbackRequest("tencent", Map.of(), payload), null, Map.of()))
                .isTrue();
    }

    @Test
    void rejectsBlankBody() {
        assertThat(verifier.verify(new CallbackRequest("tencent", Map.of(), ""), null, Map.of())).isFalse();
        assertThat(verifier.verify(new CallbackRequest("tencent", Map.of(), "   "), null, Map.of())).isFalse();
    }

    @Test
    void rejectsNonJsonPayload() {
        assertThat(verifier.verify(new CallbackRequest("tencent", Map.of(), "not-json"), null, Map.of()))
                .isFalse();
    }

    @Test
    void rejectsJsonArrayPayload() {
        assertThat(verifier.verify(new CallbackRequest("tencent", Map.of(), "[]"), null, Map.of())).isFalse();
    }
}
