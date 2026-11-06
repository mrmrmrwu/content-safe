package io.github.wrqtec.content.safe.provider.volcengine.internal;

import com.volcengine.model.response.ResponseMetadata;
import io.github.wrqtec.content.safe.api.exception.ProviderCallException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class VolcengineErrorMapperTest {

    @Test
    void sanitizesLongTokensInMessage() {
        ProviderCallException exception = VolcengineErrorMapper.fromException(
                new IllegalStateException("ak=AKLTzolEqRseWhvWa6b3muWh signature bad"));
        assertThat(exception.getMessage()).doesNotContain("AKLTzolEqRseWhvWa6b3muWh");
        assertThat(exception.getMessage()).contains("***");
    }

    @Test
    void truncatesLongVendorMessages() {
        String longMessage = "x".repeat(300);
        ProviderCallException exception = VolcengineErrorMapper.fromException(
                new IllegalStateException(longMessage));
        assertThat(exception.getMessage()).hasSizeLessThan(200);
    }

    @Test
    void nullMessageKeptEmpty() {
        ProviderCallException exception = VolcengineErrorMapper.fromException(
                new IllegalStateException());
        assertThat(exception.getMessage()).contains("IllegalStateException");
    }

    @Test
    void responseErrorCarriesCodeAndRequestId() {
        ResponseMetadata.Error error = new ResponseMetadata.Error();
        error.setCode("ZENPUTOKENINVALID");
        error.setMessage("token is invalid or expired AKLTsomeLongTokenValue123456");
        ProviderCallException exception = VolcengineErrorMapper.fromResponseError(error, "req-9");
        assertThat(exception.getMessage()).contains("ZENPUTOKENINVALID").contains("req-9");
        assertThat(exception.getMessage()).doesNotContain("AKLTsomeLongTokenValue123456");
    }

    @Test
    void sanitizeHandlesBlank() {
        assertThat(VolcengineErrorMapper.sanitize(null)).isEmpty();
        assertThat(VolcengineErrorMapper.sanitize("  ")).isEmpty();
    }
}
