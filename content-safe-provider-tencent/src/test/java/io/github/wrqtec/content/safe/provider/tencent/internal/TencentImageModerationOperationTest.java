package io.github.wrqtec.content.safe.provider.tencent.internal;

import java.util.Base64;

import com.tencentcloudapi.common.exception.TencentCloudSDKException;
import com.tencentcloudapi.ims.v20201229.models.ImageModerationRequest;
import com.tencentcloudapi.ims.v20201229.models.ImageModerationResponse;
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
 * {@link TencentImageModerationOperation} 单元测试
 *
 * @author L.David
 */
class TencentImageModerationOperationTest {

    private final TencentClientInvoker invoker = mock(TencentClientInvoker.class);
    private final TencentAuditResultNormalizer normalizer = new TencentAuditResultNormalizer();

    private final ProviderPlan plan = TencentAuditOperationProvider.syncPlan(
            "ImageModeration", "ugc-image", new OperationLimits(null, null, null, 0, 0, false));

    private final TencentImageModerationOperation operation =
            new TencentImageModerationOperation(plan, null, 2, 5, invoker, normalizer);

    @Test
    void declaresUrlBase64BytesCapabilities() {
        assertThat(operation.capabilities().contentTypes()).containsExactly(ContentType.IMAGE);
        assertThat(operation.capabilities().sync()).isTrue();
        assertThat(operation.capabilities().url()).isTrue();
        assertThat(operation.capabilities().base64()).isTrue();
        assertThat(operation.capabilities().bytes()).isTrue();
    }

    @Test
    void urlChannelAssemblesFileUrlAndGifParams() throws Exception {
        when(invoker.imageModeration(any())).thenReturn(passResponse());
        AuditRequest request = AuditRequest.image("ugc-image",
                ContentRef.url("https://example.com/a.jpg"));
        AuditResult result = operation.audit(request);

        ArgumentCaptor<ImageModerationRequest> captor = ArgumentCaptor.forClass(ImageModerationRequest.class);
        verify(invoker).imageModeration(captor.capture());
        ImageModerationRequest sent = captor.getValue();
        assertThat(sent.getFileUrl()).isEqualTo("https://example.com/a.jpg");
        assertThat(sent.getFileContent()).isNull();
        assertThat(sent.getBizType()).isEqualTo("ugc-image");
        assertThat(sent.getInterval()).isEqualTo(2L);
        assertThat(sent.getMaxFrames()).isEqualTo(5L);
        assertThat(sent.getType()).isNull();
        assertThat(result.suggestion()).isEqualTo(RiskSuggestion.PASS);
    }

    @Test
    void aigcVariantSetsTypeImageAigc() throws Exception {
        TencentImageModerationOperation aigcOperation = new TencentImageModerationOperation(
                plan, "IMAGE_AIGC", null, null, invoker, normalizer);
        when(invoker.imageModeration(any())).thenReturn(passResponse());
        aigcOperation.audit(AuditRequest.image("ugc-image", ContentRef.url("https://example.com/b.jpg")));
        ArgumentCaptor<ImageModerationRequest> captor = ArgumentCaptor.forClass(ImageModerationRequest.class);
        verify(invoker).imageModeration(captor.capture());
        assertThat(captor.getValue().getType()).isEqualTo("IMAGE_AIGC");
    }

    @Test
    void bytesChannelEncodesBase64() throws Exception {
        when(invoker.imageModeration(any())).thenReturn(passResponse());
        byte[] bytes = {1, 2, 3, 4};
        operation.audit(AuditRequest.image("ugc-image", ContentRef.bytes(bytes)));
        ArgumentCaptor<ImageModerationRequest> captor = ArgumentCaptor.forClass(ImageModerationRequest.class);
        verify(invoker).imageModeration(captor.capture());
        assertThat(captor.getValue().getFileContent())
                .isEqualTo(Base64.getEncoder().encodeToString(bytes));
    }

    @Test
    void rejectsOversizeBase64BeforeCall() {
        String oversized = "A".repeat((int) TencentLimits.maxBase64Bytes() + 1);
        AuditRequest request = AuditRequest.image("ugc-image", ContentRef.base64(oversized));
        assertThatThrownBy(() -> operation.audit(request))
                .isInstanceOf(UnsupportedContentException.class)
                .hasMessageContaining("at most");
    }

    @Test
    void rejectsTextRequestBeforeCall() {
        AuditRequest request = AuditRequest.text("ugc-image", "hello");
        assertThatThrownBy(() -> operation.audit(request))
                .isInstanceOf(UnsupportedContentException.class);
    }

    @Test
    void mapsSdkException() throws Exception {
        when(invoker.imageModeration(any())).thenThrow(
                new TencentCloudSDKException("net down", "req-y", "InternalError"));
        AuditRequest request = AuditRequest.image("ugc-image",
                ContentRef.url("https://example.com/a.jpg"));
        assertThatThrownBy(() -> operation.audit(request))
                .isInstanceOf(ProviderCallException.class)
                .hasMessageContaining("InternalError");
    }

    private ImageModerationResponse passResponse() {
        ImageModerationResponse response = new ImageModerationResponse();
        response.setRequestId("req-i1");
        response.setSuggestion("Pass");
        response.setLabel("Normal");
        return response;
    }
}
