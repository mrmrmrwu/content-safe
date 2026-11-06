package io.github.wrqtec.content.safe.provider.baidu.internal;

import java.io.IOException;

import com.google.gson.Gson;
import io.github.wrqtec.content.safe.api.exception.ProviderCallException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link BaiduErrorMapper} 单元测试
 *
 * @author L.David
 */
class BaiduErrorMapperTest {

    private static final Gson GSON = new Gson();

    @Test
    void successResponsePassesThrough() {
        BaiduCensorResponse response = GSON.fromJson(
                "{\"log_id\":1,\"conclusionType\":1}", BaiduCensorResponse.class);

        assertThatCode(() -> BaiduErrorMapper.assertSuccess(response)).doesNotThrowAnyException();
        assertThat(BaiduErrorMapper.assertSuccess(response)).isSameAs(response);
    }

    @Test
    void errorCodeThrowsProviderCallException() {
        BaiduCensorResponse response = GSON.fromJson(
                "{\"error_code\":18,\"error_msg\":\"Open api qps request limit reached\"}",
                BaiduCensorResponse.class);

        assertThatThrownBy(() -> BaiduErrorMapper.assertSuccess(response))
                .isInstanceOf(ProviderCallException.class)
                .hasMessageContaining("error_code=18");
    }

    @Test
    void tokenErrorCodesDetected() {
        assertThat(BaiduErrorMapper.isTokenError(110)).isTrue();
        assertThat(BaiduErrorMapper.isTokenError(111)).isTrue();
        assertThat(BaiduErrorMapper.isTokenError(18)).isFalse();
        assertThat(BaiduErrorMapper.isTokenError(null)).isFalse();
    }

    @Test
    void fromExceptionWrapsWithSanitizedMessage() {
        ProviderCallException exception = BaiduErrorMapper.fromException(
                new IOException("connect reset"));

        assertThat(exception).isInstanceOf(ProviderCallException.class);
        assertThat(exception.getMessage()).contains("IOException").contains("connect reset");
    }

    @Test
    void sanitizeMasksLongTokens() {
        String sanitized = BaiduErrorMapper.sanitize(
                "failed token=24.c391d358b2a5ea1895a075b244360fab.2592000.1764825739-282335-122271836 end");

        assertThat(sanitized).doesNotContain("c391d358b2a5ea1895a075b244360fab")
                .contains("***");
    }

    @Test
    void sanitizeTruncatesLongMessages() {
        // 用空格分隔的短词，避免整体被识别为长 token 而掩码
        String longMessage = "error occurred ".repeat(100);

        String sanitized = BaiduErrorMapper.sanitize(longMessage);

        assertThat(sanitized.length()).isLessThanOrEqualTo(124);
        assertThat(sanitized).endsWith("...");
    }

    @Test
    void sanitizeHandlesNull() {
        assertThat(BaiduErrorMapper.sanitize(null)).isEmpty();
    }
}
