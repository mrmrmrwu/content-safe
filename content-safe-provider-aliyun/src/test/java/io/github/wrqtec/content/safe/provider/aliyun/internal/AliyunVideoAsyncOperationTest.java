package io.github.wrqtec.content.safe.provider.aliyun.internal;

import java.util.List;
import java.util.Map;

import com.aliyun.green20220302.models.VideoModerationResponseBody;
import io.github.wrqtec.content.safe.api.AuditRequest;
import io.github.wrqtec.content.safe.api.AuditResult;
import io.github.wrqtec.content.safe.api.ContentRef;
import io.github.wrqtec.content.safe.api.enums.ContentType;
import io.github.wrqtec.content.safe.api.enums.AuditSource;
import io.github.wrqtec.content.safe.api.enums.RiskSuggestion;
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
import static org.mockito.Mockito.when;

/**
 * {@link AliyunVideoAsyncOperation} 单元测试：URL-only 门控、提交/轮询/中间态
 * （业务码 280 检测中、288 排队中）、脱敏（tempUrl/text/riskWords 不透传）
 *
 * @author L.David
 */
class AliyunVideoAsyncOperationTest {

    private static final ProviderPlan PLAN = new ProviderPlan(
            "aliyun", "green-cip", "2022-03-02", "VideoModeration", "videoDetection",
            AuditMode.ASYNC_QUERY, AliyunLimits.videoModeration(), null);

    private static final String VIDEO_URL = "https://example.com/a.mp4";

    private final GreenClientInvoker invoker = mock(GreenClientInvoker.class);

    private AliyunVideoAsyncOperation operation() {
        return new AliyunVideoAsyncOperation(PLAN, invoker, new AliyunAuditResultNormalizer());
    }

    private AuditRequest request() {
        return AuditRequest.builder()
                .scene("short_video").type(ContentType.VIDEO)
                .content(ContentRef.url(VIDEO_URL))
                .dataId("data-req")
                .build();
    }

    private AsyncTaskRef ref() {
        return new AsyncTaskRef("task-1", "data-req");
    }

    @Test
    void capabilitiesDeclareUrlOnlyAsyncQuery() {
        AliyunVideoAsyncOperation operation = operation();
        assertThat(operation.capabilities().contentTypes()).containsExactly(ContentType.VIDEO);
        assertThat(operation.capabilities().url()).isTrue();
        assertThat(operation.capabilities().base64()).isFalse();
        assertThat(operation.capabilities().asyncQuery()).isTrue();
        assertThat(operation.capabilities().asyncCallback()).isFalse();
    }

    @Test
    void auditIsNotSupported() {
        assertThatThrownBy(() -> operation().audit(request()))
                .isInstanceOf(UnsupportedFeatureException.class);
    }

    @Test
    void submitAsyncRejectsNonUrlContent() {
        AuditRequest base64Request = AuditRequest.builder()
                .scene("short_video").type(ContentType.VIDEO)
                .content(ContentRef.base64("ZmFrZQ=="))
                .build();
        assertThatThrownBy(() -> operation().submitAsync(base64Request))
                .isInstanceOf(UnsupportedContentException.class)
                .hasMessageContaining("url");
    }

    @Test
    void submitAsyncAssemblesParamsAndReturnsRef() throws Exception {
        VideoModerationResponseBody body = new VideoModerationResponseBody()
                .setCode(200)
                .setData(new VideoModerationResponseBody.VideoModerationResponseBodyData()
                        .setTaskId("task-1").setDataId("data-req"));
        when(invoker.videoModeration(eq("videoDetection"), anyMap())).thenReturn(body);

        AsyncTaskRef asyncRef = operation().submitAsync(request());

        assertThat(asyncRef.providerTaskId()).isEqualTo("task-1");
        assertThat(asyncRef.dataId()).isEqualTo("data-req");
        org.mockito.ArgumentCaptor<Map<String, String>> captor =
                org.mockito.ArgumentCaptor.forClass(Map.class);
        org.mockito.Mockito.verify(invoker).videoModeration(eq("videoDetection"), captor.capture());
        Map<String, String> params = captor.getValue();
        assertThat(params).containsEntry("url", VIDEO_URL).containsEntry("dataId", "data-req");
    }

    @Test
    void submitAsyncFailsWhenNoTaskId() throws Exception {
        VideoModerationResponseBody body = new VideoModerationResponseBody().setCode(200);
        when(invoker.videoModeration(eq("videoDetection"), anyMap())).thenReturn(body);
        assertThatThrownBy(() -> operation().submitAsync(request()))
                .isInstanceOf(ProviderCallException.class)
                .hasMessageContaining("taskId");
    }

    @Test
    void queryInProgressWhenDetecting() throws Exception {
        when(invoker.videoModerationResult(eq("videoDetection"), anyMap()))
                .thenReturn(new com.aliyun.green20220302.models.VideoModerationResultResponseBody()
                        .setCode(280).setRequestId("req-d-1"));

        AuditResult result = operation().query(ref());

        assertThat(result.ok()).isTrue();
        assertThat(result.suggestion()).isNull();
        assertThat(result.finalResult()).isFalse();
        // 处理中结果由轮询查询产生，来源必须是 ASYNC_QUERY
        assertThat(result.source()).isEqualTo(AuditSource.ASYNC_QUERY);
        assertThat(result.raw()).containsEntry("code", 280).containsEntry("requestId", "req-d-1");
    }

    @Test
    void queryInProgressWhenQueued() throws Exception {
        when(invoker.videoModerationResult(eq("videoDetection"), anyMap()))
                .thenReturn(new com.aliyun.green20220302.models.VideoModerationResultResponseBody()
                        .setCode(288));

        assertThat(operation().query(ref()).finalResult()).isFalse();
    }

    @Test
    void queryRejectCarriesMetadataOnly() throws Exception {
        Map<String, Object> sample = AliyunAuditResultNormalizerContractTest.loadJson("video-reject.json");
        var body = com.aliyun.tea.TeaModel.build(sample,
                new com.aliyun.green20220302.models.VideoModerationResultResponseBody());
        when(invoker.videoModerationResult(eq("videoDetection"), anyMap())).thenReturn(body);

        AuditResult result = operation().query(ref());

        assertThat(result.suggestion()).isEqualTo(RiskSuggestion.REJECT);
        assertThat(result.finalResult()).isTrue();
        List<io.github.wrqtec.content.safe.api.RiskHit> hits = result.hits();
        assertThat(hits).hasSize(2);
        // 帧命中：offset/timestamp/frameRiskLevel/labelSum 进 raw，tempUrl 不透传
        io.github.wrqtec.content.safe.api.RiskHit frameHit = hits.get(0);
        assertThat(frameHit.labelPath()).isEqualTo("porn");
        assertThat(frameHit.confidence()).isEqualTo(96.5D);
        assertThat(frameHit.raw()).containsEntry("offset", 12.5F).containsEntry("timestamp", 12500L)
                .containsEntry("frameRiskLevel", "high").containsEntry("labelSum", 2);
        assertThat(frameHit.raw().toString()).doesNotContain("oss.example.com");
        // 语音命中：起止偏移进 raw，转写原文/命中词不透传
        io.github.wrqtec.content.safe.api.RiskHit audioHit = hits.get(1);
        assertThat(audioHit.labels()).containsExactly("ad", "qrcode");
        assertThat(audioHit.raw()).containsEntry("startTime", 5L).containsEntry("endTime", 9L)
                .containsEntry("score", 88.2F);
        assertThat(String.valueOf(result)).doesNotContain("加微信");
    }

    @Test
    void queryPassWhenNoHits() throws Exception {
        Map<String, Object> sample = AliyunAuditResultNormalizerContractTest.loadJson("video-pass.json");
        var body = com.aliyun.tea.TeaModel.build(sample,
                new com.aliyun.green20220302.models.VideoModerationResultResponseBody());
        when(invoker.videoModerationResult(eq("videoDetection"), anyMap())).thenReturn(body);

        AuditResult result = operation().query(ref());

        assertThat(result.suggestion()).isEqualTo(RiskSuggestion.PASS);
        assertThat(result.pass()).isTrue();
        assertThat(result.requestId()).isEqualTo("req-video-pass-001");
    }
}
