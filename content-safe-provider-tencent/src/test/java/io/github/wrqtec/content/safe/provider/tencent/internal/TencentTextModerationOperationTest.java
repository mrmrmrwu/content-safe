package io.github.wrqtec.content.safe.provider.tencent.internal;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;

import com.tencentcloudapi.common.exception.TencentCloudSDKException;
import com.tencentcloudapi.tms.v20201229.models.TextModerationRequest;
import com.tencentcloudapi.tms.v20201229.models.TextModerationResponse;
import io.github.wrqtec.content.safe.api.AuditContext;
import io.github.wrqtec.content.safe.api.AuditRequest;
import io.github.wrqtec.content.safe.api.AuditResult;
import io.github.wrqtec.content.safe.api.ContentRef;
import io.github.wrqtec.content.safe.api.enums.ContentType;
import io.github.wrqtec.content.safe.api.enums.RiskSuggestion;
import io.github.wrqtec.content.safe.api.exception.ProviderCallException;
import io.github.wrqtec.content.safe.api.exception.UnsupportedContentException;
import io.github.wrqtec.content.safe.model.OperationLimits;
import io.github.wrqtec.content.safe.model.ProviderPlan;
import io.github.wrqtec.content.safe.provider.tencent.TencentAuditOperationProvider;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link TencentTextModerationOperation} 单元测试
 *
 * @author L.David
 */
class TencentTextModerationOperationTest {

    private final TencentClientInvoker invoker = mock(TencentClientInvoker.class);
    private final TencentAuditResultNormalizer normalizer = new TencentAuditResultNormalizer();

    private final ProviderPlan plan = TencentAuditOperationProvider.syncPlan(
            "TextModeration", "comment", new OperationLimits(null, null, null, 10000, 0, false));

    private final TencentTextModerationOperation operation =
            new TencentTextModerationOperation(plan, invoker, normalizer);

    @Test
    void declaresTextSyncCapabilities() {
        assertThat(operation.capabilities().contentTypes()).containsExactly(ContentType.TEXT);
        assertThat(operation.capabilities().sync()).isTrue();
    }

    @Test
    void assemblesBase64ContentAndContext() throws Exception {
        TextModerationResponse response = new TextModerationResponse();
        response.setRequestId("req-t1");
        response.setSuggestion("Pass");
        response.setLabel("Normal");
        when(invoker.textModeration(any())).thenReturn(response);

        AuditRequest request = AuditRequest.builder()
                .scene("comment")
                .type(ContentType.TEXT)
                .content(ContentRef.text("你好世界"))
                .dataId("d-100")
                .context(AuditContext.of("user-1", "device-1", "10.0.0.1"))
                .build();
        AuditResult result = operation.audit(request);

        ArgumentCaptor<TextModerationRequest> captor = ArgumentCaptor.forClass(TextModerationRequest.class);
        verify(invoker).textModeration(captor.capture());
        TextModerationRequest sent = captor.getValue();
        assertThat(Base64.getDecoder().decode(sent.getContent()))
                .isEqualTo("你好世界".getBytes(StandardCharsets.UTF_8));
        assertThat(sent.getBizType()).isEqualTo("comment");
        assertThat(sent.getDataId()).isEqualTo("d-100");
        assertThat(sent.getUser().getUserId()).isEqualTo("user-1");
        assertThat(sent.getDevice().getDeviceId()).isEqualTo("device-1");
        assertThat(sent.getDevice().getIP()).isEqualTo("10.0.0.1");
        assertThat(result.suggestion()).isEqualTo(RiskSuggestion.PASS);
        assertThat(result.cost()).isNotNull();
    }

    @Test
    void rejectsNonTextRequestBeforeCall() {
        AuditRequest request = AuditRequest.image("comment", ContentRef.url("https://example.com/a.jpg"));
        assertThatThrownBy(() -> operation.audit(request))
                .isInstanceOf(UnsupportedContentException.class)
                .hasMessageContaining("only supports text");
    }

    @Test
    void rejectsOversizeContentBeforeCall() {
        String oversized = "字".repeat(TencentLimits.maxTextChars() + 1);
        AuditRequest request = AuditRequest.text("comment", oversized);
        assertThatThrownBy(() -> operation.audit(request))
                .isInstanceOf(UnsupportedContentException.class)
                .hasMessageContaining("at most");
    }

    @Test
    void mapsSdkException() throws Exception {
        when(invoker.textModeration(any())).thenThrow(
                new TencentCloudSDKException("boom", "req-x", "Throttling"));
        AuditRequest request = AuditRequest.text("comment", "hello");
        assertThatThrownBy(() -> operation.audit(request))
                .isInstanceOf(ProviderCallException.class)
                .hasMessageContaining("Throttling")
                .hasMessageContaining("req-x");
    }
}
