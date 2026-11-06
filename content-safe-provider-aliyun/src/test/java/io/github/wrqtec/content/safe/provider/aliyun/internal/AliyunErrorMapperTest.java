package io.github.wrqtec.content.safe.provider.aliyun.internal;

import com.aliyun.tea.TeaException;
import io.github.wrqtec.content.safe.api.exception.ProviderCallException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link AliyunErrorMapper} 单元测试：错误码门控、异常映射与消息脱敏
 *
 * @author L.David
 */
class AliyunErrorMapperTest {

    @Test
    void successCodePasses() {
        AliyunErrorMapper.assertSuccess(200, "OK");
    }

    @Test
    void nonSuccessCodeRejected() {
        assertThatThrownBy(() -> AliyunErrorMapper.assertSuccess(400, "service is invalid"))
                .isInstanceOf(ProviderCallException.class)
                .hasMessageContaining("400")
                .hasMessageContaining("service is invalid");
    }

    @Test
    void nullCodeRejected() {
        assertThatThrownBy(() -> AliyunErrorMapper.assertSuccess(null, "anything"))
                .isInstanceOf(ProviderCallException.class);
    }

    @Test
    void teaExceptionKeepsCodeAndStatusOnly() {
        TeaException teaException = new TeaException();
        teaException.setCode("InvalidAccessKeyId.NotFound");
        teaException.setStatusCode(400);
        teaException.setMessage("secret=AKIDEXAMPLE123456789 signature=abcd1234abcd1234");
        ProviderCallException mapped = AliyunErrorMapper.fromException(teaException);
        assertThat(mapped.getMessage()).contains("InvalidAccessKeyId.NotFound").contains("400");
        // TeaException.message 可能携带签名材料，不允许进入异常消息
        assertThat(mapped.getMessage()).doesNotContain("signature").doesNotContain("AKIDEXAMPLE");
    }

    @Test
    void genericExceptionSanitized() {
        ProviderCallException mapped = AliyunErrorMapper.fromException(
                new RuntimeException("connect reset token=abcd1234abcd1234abcd end"));
        assertThat(mapped).isInstanceOf(ProviderCallException.class);
        assertThat(mapped.getMessage()).doesNotContain("abcd1234abcd1234abcd");
        assertThat(mapped.getMessage()).contains("RuntimeException");
    }

    @Test
    void sanitizeMasksLongTokensAndTruncates() {
        String masked = AliyunErrorMapper.sanitize("key=abcd1234abcd1234abcd ok");
        assertThat(masked).doesNotContain("abcd1234abcd1234abcd").contains("***");
        String longMessage = "x".repeat(300);
        assertThat(AliyunErrorMapper.sanitize(longMessage)).hasSizeLessThan(longMessage.length());
        assertThat(AliyunErrorMapper.sanitize(null)).isEmpty();
        assertThat(AliyunErrorMapper.sanitize("  ")).isEmpty();
    }
}
