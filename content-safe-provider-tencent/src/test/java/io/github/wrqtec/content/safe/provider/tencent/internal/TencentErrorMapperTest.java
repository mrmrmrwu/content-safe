package io.github.wrqtec.content.safe.provider.tencent.internal;

import com.tencentcloudapi.common.exception.TencentCloudSDKException;
import io.github.wrqtec.content.safe.api.exception.ProviderCallException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link TencentErrorMapper} 单元测试（安全约束：消息不含密钥/签名串）
 *
 * @author L.David
 */
class TencentErrorMapperTest {

    @Test
    void sdkExceptionKeepsOnlyErrorCodeAndRequestId() {
        TencentCloudSDKException sdkException = new TencentCloudSDKException(
                "secret=AKIDxxxxxxxxxxxx&Signature=abcd1234abcd1234abcd1234abcd1234 signature leaked",
                "req-100", "AuthFailure.SignatureFailure");
        ProviderCallException mapped = TencentErrorMapper.fromException(sdkException);
        assertThat(mapped.getMessage()).contains("AuthFailure.SignatureFailure").contains("req-100");
        // message 可能携带签名材料：映射后绝不保留
        assertThat(mapped.getMessage()).doesNotContain("Signature=").doesNotContain("AKID");
        assertThat(mapped.getCause()).isSameAs(sdkException);
    }

    @Test
    void genericExceptionIsSanitized() {
        ProviderCallException mapped = TencentErrorMapper.fromException(
                new IllegalStateException("token abcdef0123456789abcdef0123456789 expired"));
        assertThat(mapped.getMessage()).contains("IllegalStateException");
        assertThat(mapped.getMessage()).doesNotContain("abcdef0123456789");
        assertThat(mapped.getMessage()).contains("***");
    }

    @Test
    void sanitizeMasksAndTruncates() {
        assertThat(TencentErrorMapper.sanitize(null)).isEmpty();
        assertThat(TencentErrorMapper.sanitize(" ")).isEmpty();
        String longMessage = "x".repeat(200);
        String sanitized = TencentErrorMapper.sanitize(longMessage);
        // 长 token 先掩码（只剩 ***），再截断到 120
        assertThat(sanitized.length()).isLessThanOrEqualTo(123);
    }
}
