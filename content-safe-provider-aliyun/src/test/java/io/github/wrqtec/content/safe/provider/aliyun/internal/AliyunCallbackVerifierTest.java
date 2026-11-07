package io.github.wrqtec.content.safe.provider.aliyun.internal;

import java.util.Map;
import java.util.TreeMap;

import io.github.wrqtec.content.safe.api.CallbackRequest;
import io.github.wrqtec.content.safe.provider.aliyun.AliyunProviderConstants;
import io.github.wrqtec.content.safe.provider.aliyun.AliyunProviderProperties;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link AliyunCallbackVerifier} 单元测试：SHA256 验签、表单解析、配置缺失
 *
 * @author L.David
 */
class AliyunCallbackVerifierTest {

    private static final String UID = "1234567890";
    private static final String SEED = "seed-1";

    private final AliyunProviderProperties properties = new AliyunProviderProperties();

    private AliyunCallbackVerifier verifier() {
        properties.getExtra().put(AliyunProviderConstants.EXTRA_CALLBACK_UID, UID);
        properties.getExtra().put(AliyunProviderConstants.EXTRA_CALLBACK_SEED, SEED);
        return new AliyunCallbackVerifier(properties);
    }

    private String form(String content, String checksum) {
        return "ReqId=req-1&Checksum=" + checksum + "&Content=" + content;
    }

    @Test
    void acceptsValidChecksum() {
        String content = "{\"dataId\":\"d1\",\"riskLevel\":\"high\"}";
        String checksum = AliyunCallbackVerifier.sha256Hex(UID + SEED + content);
        boolean pass = verifier().verify(
                new CallbackRequest("aliyun", Map.of(), form(content, checksum)), null, Map.of());
        assertThat(pass).isTrue();
    }

    @Test
    void acceptsUppercaseChecksum() {
        String content = "{\"dataId\":\"d1\"}";
        String checksum = AliyunCallbackVerifier.sha256Hex(UID + SEED + content).toUpperCase();
        boolean pass = verifier().verify(
                new CallbackRequest("aliyun", Map.of(), form(content, checksum)), null, Map.of());
        assertThat(pass).isTrue();
    }

    @Test
    void fallsBackToGlobalSecretWhenSeedAbsent() {
        properties.getExtra().put(AliyunProviderConstants.EXTRA_CALLBACK_UID, UID);
        AliyunCallbackVerifier verifier = new AliyunCallbackVerifier(properties);
        String content = "{\"dataId\":\"d2\"}";
        String checksum = AliyunCallbackVerifier.sha256Hex(UID + "global-secret" + content);
        boolean pass = verifier.verify(
                new CallbackRequest("aliyun", Map.of(), form(content, checksum)), "global-secret", Map.of());
        assertThat(pass).isTrue();
    }

    @Test
    void rejectsTamperedContent() {
        String content = "{\"dataId\":\"d1\"}";
        String checksum = AliyunCallbackVerifier.sha256Hex(UID + SEED + "{\"dataId\":\"other\"}");
        boolean pass = verifier().verify(
                new CallbackRequest("aliyun", Map.of(), form(content, checksum)), null, Map.of());
        assertThat(pass).isFalse();
    }

    @Test
    void rejectsWhenChecksumMissing() {
        boolean pass = verifier().verify(
                new CallbackRequest("aliyun", Map.of(), "ReqId=req-1&Content=abc"), null, Map.of());
        assertThat(pass).isFalse();
    }

    @Test
    void rejectsWhenUidNotConfigured() {
        AliyunCallbackVerifier verifier = new AliyunCallbackVerifier(properties);
        String content = "{\"dataId\":\"d1\"}";
        String checksum = AliyunCallbackVerifier.sha256Hex("" + SEED + content);
        boolean pass = verifier.verify(
                new CallbackRequest("aliyun", Map.of(), form(content, checksum)), null, Map.of());
        assertThat(pass).isFalse();
    }

    @Test
    void parseFormDecodesUrlEncodedValue() {
        Map<String, String> form = AliyunCallbackVerifier.parseForm(
                "ReqId=req%2F1&Checksum=ab%3Dcd&Content=%7B%22a%22%3A1%7D");
        assertThat(form)
                .containsEntry("ReqId", "req/1")
                .containsEntry("Checksum", "ab=cd")
                .containsEntry("Content", "{\"a\":1}");
    }

    @Test
    void parseFormHandlesEmptyBody() {
        assertThat(AliyunCallbackVerifier.parseForm(null)).isEmpty();
        assertThat(AliyunCallbackVerifier.parseForm("   ")).isEmpty();
    }

    @Test
    void parseFormKeepsInsertionOrder() {
        Map<String, String> form = AliyunCallbackVerifier.parseForm("b=2&a=1");
        assertThat(new TreeMap<>(form)).containsEntry("a", "1").containsEntry("b", "2");
        assertThat(form.keySet().iterator().next()).isEqualTo("b");
    }
}
