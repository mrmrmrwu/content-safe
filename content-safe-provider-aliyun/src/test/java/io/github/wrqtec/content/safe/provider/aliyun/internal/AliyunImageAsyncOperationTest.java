package io.github.wrqtec.content.safe.provider.aliyun.internal;

import java.util.List;
import java.util.Map;

import com.aliyun.green20220302.models.DescribeImageModerationResultResponseBody;
import com.aliyun.green20220302.models.ImageAsyncModerationResponseBody;
import io.github.wrqtec.content.safe.api.AuditRequest;
import io.github.wrqtec.content.safe.api.AuditResult;
import io.github.wrqtec.content.safe.api.CallbackRequest;
import io.github.wrqtec.content.safe.api.CallbackResult;
import io.github.wrqtec.content.safe.api.ContentRef;
import io.github.wrqtec.content.safe.api.enums.ContentType;
import io.github.wrqtec.content.safe.api.enums.AuditSource;
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
        return AuditRequest.builder()
                .scene("avatar").type(ContentType.IMAGE)
                .content(ContentRef.url(IMAGE_URL))
                .dataId("data-req")
                .build();
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
        verify(invoker).imageAsyncModeration("baselineCheck",
                Map.of("imageUrl", IMAGE_URL, "dataId", "data-req"));
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
                "dataId", "data-req",
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
        // 处理中结果由轮询查询产生，来源必须是 ASYNC_QUERY
        assertThat(result.source()).isEqualTo(AuditSource.ASYNC_QUERY);
        assertThat(result.requestId()).isEqualTo("r-9");
    }

    @Test
    void queryRequiresTaskId() {
        // AsyncTaskRef 构造已强制 providerTaskId 非空，此处验证 query 内部断言为纵深防御
        assertThatThrownBy(() -> operation().query(new AsyncTaskRef(" ", null)))
                .isInstanceOf(InvalidRequestException.class);
    }

    @Test
    void handleCallbackParsesFormContentAndAccepts() throws Exception {
        // 回调 Content 与查询 Data 同构（官方 2638108，camelCase JSON），验签由 dispatcher 前置完成
        String content = new com.google.gson.Gson()
                .toJson(AliyunAuditResultNormalizerContractTest.loadJson("image-callback-content.json"));
        String rawBody = "ReqId=req-cb-001&Checksum=ignored&Content="
                + java.net.URLEncoder.encode(content, java.nio.charset.StandardCharsets.UTF_8);

        CallbackResult result = operation().handleCallback(new CallbackRequest("aliyun", Map.of(), rawBody));

        assertThat(result.accepted()).isTrue();
        // Content.reqId 即提交时返回的 reqId（存储的任务引用）
        assertThat(result.taskId()).isEqualTo("req-cb-001");
        assertThat(result.result().suggestion()).isEqualTo(RiskSuggestion.REJECT);
        assertThat(result.result().hits()).hasSize(1);
        // frame 字段内的截图 URL 不透传（铁律 §5.6）
        assertThat(String.valueOf(result)).doesNotContain("oss.example.com");
    }

    @Test
    void handleCallbackRejectsMissingContent() {
        CallbackResult result = operation().handleCallback(
                new CallbackRequest("aliyun", Map.of(), "ReqId=req-1&Checksum=abc"));
        assertThat(result.accepted()).isFalse();
    }

    @Test
    void handleCallbackRejectsNonObjectContent() {
        String rawBody = "ReqId=req-1&Content="
                + java.net.URLEncoder.encode("[1,2]", java.nio.charset.StandardCharsets.UTF_8);
        CallbackResult result = operation().handleCallback(new CallbackRequest("aliyun", Map.of(), rawBody));
        assertThat(result.accepted()).isFalse();
        assertThat(result.message()).doesNotContain("[1,2]");
    }
}
