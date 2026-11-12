package io.github.wrqtec.content.safe.provider.shumei.internal;

import java.io.IOException;

import com.google.gson.Gson;
import io.github.wrqtec.content.safe.api.exception.ProviderCallException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link ShumeiErrorMapper} 单元测试
 *
 * @author L.David
 */
class ShumeiErrorMapperTest {

    private static final Gson GSON = new Gson();

    @Test
    void successResponsePassesThrough() {
        ShumeiSyncResponse response = GSON.fromJson(
                "{\"code\":1100,\"riskLevel\":\"PASS\"}", ShumeiSyncResponse.class);

        assertThatCode(() -> ShumeiErrorMapper.assertSuccess(response, "detect")).doesNotThrowAnyException();
        assertThat(ShumeiErrorMapper.assertSuccess(response, "detect")).isSameAs(response);
    }

    @Test
    void qpsLimitThrowsProviderCallException() {
        ShumeiSyncResponse response = GSON.fromJson(
                "{\"code\":1901,\"message\":\"请求QPS超过限制\"}", ShumeiSyncResponse.class);

        assertThatThrownBy(() -> ShumeiErrorMapper.assertSuccess(response, "detect"))
                .isInstanceOf(ProviderCallException.class)
                .hasMessageContaining("code=1901");
    }

    @Test
    void nullCodeTreatedAsFailure() {
        ShumeiSyncResponse response = new ShumeiSyncResponse();

        assertThatThrownBy(() -> ShumeiErrorMapper.assertSuccess(response, "detect"))
                .isInstanceOf(ProviderCallException.class);
    }

    @Test
    void fromExceptionWrapsWithSanitizedMessage() {
        ProviderCallException exception = ShumeiErrorMapper.fromException(
                new IOException("connection reset"));

        assertThat(exception).isInstanceOf(ProviderCallException.class);
        assertThat(exception.getMessage()).contains("IOException").contains("connection reset");
    }

    @Test
    void sanitizeMasksLongTokens() {
        String sanitized = ShumeiErrorMapper.sanitize(
                "failed accessKey=Ab12cd34Ef56gh78Ij90kl12 end");

        assertThat(sanitized).doesNotContain("Ab12cd34Ef56gh78Ij90kl12").contains("***");
    }

    @Test
    void sanitizeTruncatesLongMessages() {
        // 用空格分隔的短词，避免整体被识别为长 token 而掩码
        String longMessage = "error occurred ".repeat(100);

        String sanitized = ShumeiErrorMapper.sanitize(longMessage);

        assertThat(sanitized.length()).isLessThanOrEqualTo(120);
    }

    @Test
    void sanitizeHandlesNull() {
        assertThat(ShumeiErrorMapper.sanitize(null)).isEmpty();
    }
}
