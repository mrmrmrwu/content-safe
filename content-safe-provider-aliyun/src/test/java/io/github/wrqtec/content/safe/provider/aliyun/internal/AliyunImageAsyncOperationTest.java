package io.github.wrqtec.content.safe.provider.aliyun.internal;

import java.util.List;
import java.util.Map;

import com.aliyun.green20220302.models.DescribeImageModerationResultResponseBody;
import com.aliyun.green20220302.models.ImageAsyncModerationResponseBody;
import io.github.wrqtec.content.safe.api.AuditRequest;
import io.github.wrqtec.content.safe.api.AuditResult;
import io.github.wrqtec.content.safe.api.ContentRef;
import io.github.wrqtec.content.safe.api.enums.ContentType;
import io.github.wrqtec.content.safe.api.enums.RiskSuggestion;
import io.github.wrqtec.content.safe.api.exception.InvalidRequestException;
import io.github.wrqtec.content.safe.api.exception.ProviderCallException;
import io.github.wrqtec.content.safe.api.exception.UnsupportedContentException;
import io.github.wrqtec.content.safe.api.exception.UnsupportedFeatureException;
import io.github.wrqtec.content.safe.model.AsyncTaskRef;
import io.github.wrqtec.content.safe.model.ProviderPlan;
import io.github.wrqtec.content.safe.model.enums.AuditMode;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link AliyunImageAsyncOperation} 单元测试：URL-only 门控、提交/轮询/中间态
 *
 * @author L.David
 */
class AliyunImageAsyncOperationTest {

    private static final ProviderPlan PLAN = new ProviderPlan(
            "aliyun", "green-cip", "2022-03-02", "ImageAsyncModeration", "baselineCheck",
            AuditMode.ASYNC_QUERY, AliyunLimits.imageAsync(), null);

    private static final String IMAGE_URL = "https://example.com/a.jpg";

    private final GreenClientInvoker invoker = mock(GreenClientInvoker.class);

    private AliyunImageAsyncOperation operation() {
        return new AliyunImageAsyncOperation(PLAN, invoker, new AliyunAuditResultNormalizer(), null, null);
    }

    private AuditRequest request() {
        return AuditRequest.image("avatar", ContentRef.url(IMAGE_URL));
    }

    @Test
    void capabilitiesDeclareUrlOnlyAsyncQuery() {
        AliyunImageAsyncOperation operation = operation();
        assertThat(operation.capabilities().contentTypes()).containsExactly(ContentType.IMAGE);
        assertThat(operation.capabilities().url()).isTrue();
        assertThat(operation.capabilities().base64()).isFalse();
        assertThat(operation.capabilities().asyncQuery()).isTrue();
        assertThat(operation.capabilities().asyncCallback()).isFalse();
    }

    @Test
    void capabilitiesDeclareCallbackWhenConfigured() {
        AliyunImageAsyncOperation operation = new AliyunImageAsyncOperation(
                PLAN, invoker, new AliyunAuditResultNormalizer(), "https://biz.example/callback", "seed-1");
        assertThat(operation.capabilities().asyncCallback()).isTrue();
    }

    @Test
    void auditIsNotSupported() {
        assertThatThrownBy(() -> operation().audit(request()))
                .isInstanceOf(UnsupportedFeatureException.class);
    }

    @Test
    void submitAsyncAssemblesParamsAndReturnsRef() throws Exception {
        ImageAsyncModerationResponseBody body = new ImageAsyncModerationResponseBody()
                .setCode(200)
                .setData(new ImageAsyncModerationResponseBody.ImageAsyncModerationResponseBodyData()
                        .setReqId("req-1").setDataId("data-1"));
        when(invoker.imageAsyncModeration(eq("baselineCheck"), anyMap())).thenReturn(body);

        AsyncTaskRef ref = operation().submitAsync(request());

        assertThat(ref.providerTaskId()).isEqualTo("req-1");
        assertThat(ref.dataId()).isEqualTo("data-1");
        verify(invoker).imageAsyncModeration("baselineCheck", Map.of("imageUrl", IMAGE_URL));
    }

    @Test
    void submitAsyncPassesCallbackConfig() throws Exception {
        ImageAsyncModerationResponseBody body = new ImageAsyncModerationResponseBody()
                .setCode(200)
                .setData(new ImageAsyncModerationResponseBody.ImageAsyncModerationResponseBodyData()
                        .setReqId("req-2"));
        when(invoker.imageAsyncModeration(eq("baselineCheck"), anyMap())).thenReturn(body);

        AliyunImageAsyncOperation operation = new AliyunImageAsyncOperation(
                PLAN, invoker, new AliyunAuditResultNormalizer(), "https://biz.example/callback", "seed-1");
        operation.submitAsync(request());

        verify(invoker).imageAsyncModeration("baselineCheck", Map.of(
                "imageUrl", IMAGE_URL,
                "callback", "https://biz.example/callback",
                "seed", "seed-1"));
    }

    @Test
    void submitAsyncRejectsBase64Content() {
        AuditRequest request = AuditRequest.builder()
                .scene("avatar").type(ContentType.IMAGE)
                .content(ContentRef.base64("aGVsbG8="))
                .build();
        assertThatThrownBy(() -> operation().submitAsync(request))
                .isInstanceOf(UnsupportedContentException.class);
    }

    @Test
    void submitAsyncFailsWhenNoReqId() throws Exception {
        ImageAsyncModerationResponseBody body = new ImageAsyncModerationResponseBody()
                .setCode(200)
                .setData(new ImageAsyncModerationResponseBody.ImageAsyncModerationResponseBodyData());
        when(invoker.imageAsyncModeration(eq("baselineCheck"), anyMap())).thenReturn(body);
        assertThatThrownBy(() -> operation().submitAsync(request()))
                .isInstanceOf(ProviderCallException.class)
                .hasMessageContaining("reqId");
    }

    @Test
    void submitAsyncMapsErrorCode() throws Exception {
        ImageAsyncModerationResponseBody body = new ImageAsyncModerationResponseBody()
                .setCode(400).setMsg("invalid serviceParameters");
        when(invoker.imageAsyncModeration(eq("baselineCheck"), anyMap())).thenReturn(body);
        assertThatThrownBy(() -> operation().submitAsync(request()))
                .isInstanceOf(ProviderCallException.class)
                .hasMessageContaining("400");
    }

    @Test
    void queryNormalizesTerminalResult() throws Exception {
        DescribeImageModerationResultResponseBody body = new DescribeImageModerationResultResponseBody()
                .setCode(200)
                .setData(new DescribeImageModerationResultResponseBody.DescribeImageModerationResultResponseBodyData()
                        .setReqId("req-1").setDataId("data-1").setRiskLevel("high")
                        .setResult(List.of(new DescribeImageModerationResultResponseBody
                                .DescribeImageModerationResultResponseBodyDataResult()
                                .setLabel("ad").setConfidence(96.5F).setRiskLevel("high"))));
        when(invoker.describeImageModerationResult("req-1")).thenReturn(body);

        AuditResult result = operation().query(new AsyncTaskRef("req-1", "data-1"));

        assertThat(result.suggestion()).isEqualTo(RiskSuggestion.REJECT);
        assertThat(result.finalResult()).isTrue();
        assertThat(result.dataId()).isEqualTo("data-1");
        assertThat(result.hits()).hasSize(1);
        assertThat(result.hits().get(0).labelPath()).isEqualTo("ad");
    }

    @Test
    void queryDetectingCodeReturnsNonFinalResult() throws Exception {
        DescribeImageModerationResultResponseBody body = new DescribeImageModerationResultResponseBody()
                .setCode(280).setMsg("detecting").setRequestId("r-9");
        when(invoker.describeImageModerationResult("req-1")).thenReturn(body);

        AuditResult result = operation().query(new AsyncTaskRef("req-1", "data-1"));

        assertThat(result.ok()).isTrue();
        assertThat(result.finalResult()).isFalse();
        assertThat(result.suggestion()).isNull();
        assertThat(result.requestId()).isEqualTo("r-9");
    }

    @Test
    void queryRequiresTaskId() {
        // AsyncTaskRef 构造已强制 providerTaskId 非空，此处验证 query 内部断言为纵深防御
        assertThatThrownBy(() -> operation().query(new AsyncTaskRef(" ", null)))
                .isInstanceOf(InvalidRequestException.class);
    }
}
