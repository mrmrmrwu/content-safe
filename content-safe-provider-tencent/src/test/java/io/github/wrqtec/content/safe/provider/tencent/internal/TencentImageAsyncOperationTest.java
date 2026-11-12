package io.github.wrqtec.content.safe.provider.tencent.internal;

import java.util.Base64;
import java.util.Map;

import com.tencentcloudapi.common.exception.TencentCloudSDKException;
import com.tencentcloudapi.ims.v20201229.models.CreateImageModerationAsyncTaskRequest;
import com.tencentcloudapi.ims.v20201229.models.CreateImageModerationAsyncTaskResponse;
import com.tencentcloudapi.ims.v20201229.models.ImageModerationResponse;
import io.github.wrqtec.content.safe.api.AuditOptions;
import io.github.wrqtec.content.safe.api.AuditRequest;
import io.github.wrqtec.content.safe.api.AuditResult;
import io.github.wrqtec.content.safe.api.CallbackRequest;
import io.github.wrqtec.content.safe.api.CallbackResult;
import io.github.wrqtec.content.safe.api.ContentRef;
import io.github.wrqtec.content.safe.api.enums.AuditSource;
import io.github.wrqtec.content.safe.api.enums.ContentType;
import io.github.wrqtec.content.safe.api.enums.RiskSuggestion;
import io.github.wrqtec.content.safe.api.exception.ConfigInvalidException;
import io.github.wrqtec.content.safe.api.exception.InvalidRequestException;
import io.github.wrqtec.content.safe.api.exception.ProviderCallException;
import io.github.wrqtec.content.safe.api.exception.UnsupportedContentException;
import io.github.wrqtec.content.safe.api.exception.UnsupportedFeatureException;
import io.github.wrqtec.content.safe.model.AsyncTaskRef;
import io.github.wrqtec.content.safe.provider.tencent.TencentAuditOperationProvider;
import io.github.wrqtec.content.safe.provider.tencent.TencentProviderConstants;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link TencentImageAsyncOperation} 单元测试
 *
 * @author L.David
 */
class TencentImageAsyncOperationTest {

    private static final String CALLBACK_URL = "https://biz.example.com/callback";

    private final TencentClientInvoker invoker = mock(TencentClientInvoker.class);
    private final TencentAuditResultNormalizer normalizer = new TencentAuditResultNormalizer();

    private final TencentImageAsyncOperation operation = new TencentImageAsyncOperation(
            TencentAuditOperationProvider.asyncPlan("ugc-image"), null, 2, 5, CALLBACK_URL, invoker, normalizer);

    @Test
    void declaresAsyncCallbackCapabilitiesWithoutSync() {
        assertThat(operation.capabilities().contentTypes()).containsExactly(ContentType.IMAGE);
        assertThat(operation.capabilities().sync()).isFalse();
        assertThat(operation.capabilities().url()).isTrue();
        assertThat(operation.capabilities().base64()).isTrue();
        assertThat(operation.capabilities().bytes()).isTrue();
        assertThat(operation.capabilities().asyncCallback()).isTrue();
        assertThat(operation.capabilities().asyncQuery()).isFalse();
    }

    @Test
    void auditAndQueryAreUnsupported() {
        assertThatThrownBy(() -> operation.audit(AuditRequest.image("ugc-image",
                ContentRef.url("https://example.com/a.jpg"))))
                .isInstanceOf(UnsupportedFeatureException.class);
        assertThatThrownBy(() -> operation.query(new AsyncTaskRef("task-1", null)))
                .isInstanceOf(UnsupportedFeatureException.class);
    }

    @Test
    void missingCallbackUrlFailsFast() {
        TencentImageAsyncOperation noCallback = new TencentImageAsyncOperation(
                TencentAuditOperationProvider.asyncPlan("ugc-image"), null, null, null, null, invoker, normalizer);
        assertThatThrownBy(() -> noCallback.submitAsync(
                imageRequest("img-1", ContentRef.url("https://example.com/a.jpg"))))
                .isInstanceOf(ConfigInvalidException.class)
                .hasMessageContaining("callback-url");
    }

    @Test
    void missingDataIdRejected() {
        // 工厂已自动生成 dataId，这里显式传空白验证纵深防御
        assertThatThrownBy(() -> operation.submitAsync(AuditRequest.builder()
                .scene("ugc-image").type(ContentType.IMAGE)
                .content(ContentRef.url("https://example.com/a.jpg"))
                .dataId(" ")
                .build()))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("dataId");
    }

    @Test
    void submitAsyncAssemblesTaskFieldsAndReturnsDataIdRef() throws Exception {
        when(invoker.createImageModerationAsyncTask(any())).thenReturn(taskResponse("img-1"));
        AsyncTaskRef ref = operation.submitAsync(
                imageRequest("img-1", ContentRef.url("https://example.com/a.jpg")));

        ArgumentCaptor<CreateImageModerationAsyncTaskRequest> captor =
                ArgumentCaptor.forClass(CreateImageModerationAsyncTaskRequest.class);
        verify(invoker).createImageModerationAsyncTask(captor.capture());
        CreateImageModerationAsyncTaskRequest sent = captor.getValue();
        assertThat(sent.getFileUrl()).isEqualTo("https://example.com/a.jpg");
        assertThat(sent.getCallbackUrl()).isEqualTo(CALLBACK_URL);
        assertThat(sent.getDataId()).isEqualTo("img-1");
        assertThat(sent.getBizType()).isEqualTo("ugc-image");
        assertThat(sent.getInterval()).isEqualTo(2L);
        assertThat(sent.getMaxFrames()).isEqualTo(5L);
        assertThat(ref.providerTaskId()).isEqualTo("img-1");
        assertThat(ref.dataId()).isEqualTo("img-1");
    }

    @Test
    void submitAsyncMapsUrlChannels() throws Exception {
        when(invoker.createImageModerationAsyncTask(any())).thenReturn(taskResponse("img-2"));
        operation.submitAsync(imageRequest("img-2", ContentRef.url("https://example.com/b.jpg")));
        ArgumentCaptor<CreateImageModerationAsyncTaskRequest> captor =
                ArgumentCaptor.forClass(CreateImageModerationAsyncTaskRequest.class);
        verify(invoker).createImageModerationAsyncTask(captor.capture());
        assertThat(captor.getValue().getFileUrl()).isEqualTo("https://example.com/b.jpg");
    }

    @Test
    void submitAsyncMapsBytesToBase64() throws Exception {
        when(invoker.createImageModerationAsyncTask(any())).thenReturn(taskResponse("img-3"));
        operation.submitAsync(imageRequest("img-3", ContentRef.bytes(new byte[] {1, 2, 3})));
        ArgumentCaptor<CreateImageModerationAsyncTaskRequest> captor =
                ArgumentCaptor.forClass(CreateImageModerationAsyncTaskRequest.class);
        verify(invoker).createImageModerationAsyncTask(captor.capture());
        assertThat(captor.getValue().getFileContent())
                .isEqualTo(Base64.getEncoder().encodeToString(new byte[] {1, 2, 3}));
    }

    @Test
    void oversizedBase64Rejected() {
        String big = "A".repeat((int) TencentLimits.maxBase64Bytes() + 1);
        assertThatThrownBy(() -> operation.submitAsync(imageRequest("img-big", ContentRef.base64(big))))
                .isInstanceOf(UnsupportedContentException.class)
                .hasMessageContaining("base64");
    }

    @Test
    void submitAsyncWrapsSdkError() throws Exception {
        when(invoker.createImageModerationAsyncTask(any()))
                .thenThrow(new TencentCloudSDKException("InternalError", "boom"));
        assertThatThrownBy(() -> operation.submitAsync(
                imageRequest("img-4", ContentRef.url("https://example.com/c.jpg"))))
                .isInstanceOf(ProviderCallException.class);
    }

    @Test
    void handleCallbackNormalizesPayloadWithAsyncSource() {
        String payload = "{\"DataId\":\"img-1\",\"Label\":\"Porn\",\"Suggestion\":\"Block\","
                + "\"SubLabel\":\"\",\"Score\":98}";
        CallbackResult callbackResult = operation.handleCallback(
                new CallbackRequest("tencent", Map.of(), payload));
        assertThat(callbackResult.accepted()).isTrue();
        assertThat(callbackResult.taskId()).isEqualTo("img-1");
        assertThat(callbackResult.result().source()).isEqualTo(AuditSource.ASYNC_CALLBACK);
        assertThat(callbackResult.result().suggestion()).isEqualTo(RiskSuggestion.REJECT);
        assertThat(callbackResult.result().hits()).isNotEmpty();
    }

    @Test
    void handleCallbackRejectsInvalidPayload() {
        CallbackResult callbackResult = operation.handleCallback(
                new CallbackRequest("tencent", Map.of(), "not-json"));
        assertThat(callbackResult.accepted()).isFalse();
        assertThat(callbackResult.message()).contains("invalid tencent callback payload");
    }

    @Test
    void handleCallbackRejectsMissingDataId() {
        CallbackResult callbackResult = operation.handleCallback(
                new CallbackRequest("tencent", Map.of(), "{\"Suggestion\":\"Block\"}"));
        assertThat(callbackResult.accepted()).isFalse();
        assertThat(callbackResult.message()).contains("DataId");
    }

    private AuditRequest imageRequest(String dataId, ContentRef content) {
        return AuditRequest.builder()
                .scene("ugc-image")
                .type(ContentType.IMAGE)
                .content(content)
                .dataId(dataId)
                .build();
    }

    private CreateImageModerationAsyncTaskResponse taskResponse(String dataId) {
        CreateImageModerationAsyncTaskResponse response = new CreateImageModerationAsyncTaskResponse();
        response.setDataId(dataId);
        response.setRequestId("req-1");
        return response;
    }

    @Test
    void submitAsyncSetsConfiguredType() throws Exception {
        TencentImageAsyncOperation aigcOperation = new TencentImageAsyncOperation(
                TencentAuditOperationProvider.asyncPlan("ugc-image"), TencentProviderConstants.IMAGE_TYPE_AIGC, null, null, CALLBACK_URL,
                invoker, normalizer);
        when(invoker.createImageModerationAsyncTask(any())).thenReturn(taskResponse("img-5"));
        aigcOperation.submitAsync(imageRequest("img-5", ContentRef.url("https://example.com/d.jpg")));
        ArgumentCaptor<CreateImageModerationAsyncTaskRequest> captor =
                ArgumentCaptor.forClass(CreateImageModerationAsyncTaskRequest.class);
        verify(invoker).createImageModerationAsyncTask(captor.capture());
        assertThat(captor.getValue().getType()).isEqualTo("IMAGE_AIGC");
    }

    @Test
    void submitAsyncAigcOptionOverridesConfiguredType() throws Exception {
        // 场景未按 AIGC BizType 映射（type=null），请求期显式开启 AIGC 检测同样生效
        when(invoker.createImageModerationAsyncTask(any())).thenReturn(taskResponse("img-6"));
        AuditRequest request = AuditRequest.builder()
                .scene("ugc-image").type(ContentType.IMAGE)
                .content(ContentRef.url("https://example.com/e.jpg"))
                .dataId("img-6")
                .options(AuditOptions.defaults().withAigcDetect(true))
                .build();
        operation.submitAsync(request);
        ArgumentCaptor<CreateImageModerationAsyncTaskRequest> captor =
                ArgumentCaptor.forClass(CreateImageModerationAsyncTaskRequest.class);
        verify(invoker).createImageModerationAsyncTask(captor.capture());
        assertThat(captor.getValue().getType()).isEqualTo("IMAGE_AIGC");
    }
}
