package io.github.wrqtec.content.safe.provider.tencent.internal;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

import com.tencentcloudapi.common.AbstractModel;
import com.tencentcloudapi.vm.v20210922.models.CreateVideoModerationTaskRequest;
import com.tencentcloudapi.vm.v20210922.models.CreateVideoModerationTaskResponse;
import com.tencentcloudapi.vm.v20210922.models.DescribeTaskDetailRequest;
import com.tencentcloudapi.vm.v20210922.models.DescribeTaskDetailResponse;
import io.github.wrqtec.content.safe.api.AuditRequest;
import io.github.wrqtec.content.safe.api.AuditResult;
import io.github.wrqtec.content.safe.api.CallbackRequest;
import io.github.wrqtec.content.safe.api.CallbackResult;
import io.github.wrqtec.content.safe.api.ContentRef;
import io.github.wrqtec.content.safe.api.enums.AuditSource;
import io.github.wrqtec.content.safe.api.enums.ContentType;
import io.github.wrqtec.content.safe.api.enums.RiskSuggestion;
import io.github.wrqtec.content.safe.api.exception.InvalidRequestException;
import io.github.wrqtec.content.safe.api.exception.ProviderCallException;
import io.github.wrqtec.content.safe.api.exception.UnsupportedContentException;
import io.github.wrqtec.content.safe.api.exception.UnsupportedFeatureException;
import io.github.wrqtec.content.safe.model.AsyncTaskRef;
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
 * {@link TencentVideoAsyncOperation} 单元测试：契约样本经
 * {@link AbstractModel#fromJsonString} 还原 SDK 模型驱动（与线上报文一致）
 *
 * @author L.David
 */
class TencentVideoAsyncOperationTest {

    private static final String CONTRACT_DIR = "/contracts/tencent/";

    private static final String CALLBACK_URL = "https://biz.example.com/callback";

    private final TencentClientInvoker invoker = mock(TencentClientInvoker.class);
    private final TencentAuditResultNormalizer normalizer = new TencentAuditResultNormalizer();

    private final TencentVideoAsyncOperation operation = new TencentVideoAsyncOperation(
            TencentAuditOperationProvider.videoPlan("short_video"), CALLBACK_URL, invoker, normalizer);

    @Test
    void declaresVideoUrlAsyncQueryCapabilities() {
        assertThat(operation.capabilities().contentTypes()).containsExactly(ContentType.VIDEO);
        assertThat(operation.capabilities().sync()).isFalse();
        assertThat(operation.capabilities().url()).isTrue();
        assertThat(operation.capabilities().asyncQuery()).isTrue();
        assertThat(operation.capabilities().asyncCallback()).isTrue();
    }

    @Test
    void callbackCapabilityFollowsConfiguration() {
        TencentVideoAsyncOperation pollOnly = new TencentVideoAsyncOperation(
                TencentAuditOperationProvider.videoPlan("short_video"), null, invoker, normalizer);
        assertThat(pollOnly.capabilities().asyncCallback()).isFalse();
    }

    @Test
    void auditIsNotSupported() {
        assertThatThrownBy(() -> operation.audit(videoRequest("data-1",
                ContentRef.url("https://example.com/v.mp4"))))
                .isInstanceOf(UnsupportedFeatureException.class);
    }

    @Test
    void submitAsyncRejectsNonUrlContent() {
        assertThatThrownBy(() -> operation.submitAsync(videoRequest("data-1",
                ContentRef.base64("aGVsbG8="))))
                .isInstanceOf(UnsupportedContentException.class)
                .hasMessageContaining("url only");
    }

    @Test
    void submitAsyncAssemblesParamsAndReturnsTaskId() throws Exception {
        CreateVideoModerationTaskResponse response =
                load("video-submit.json", CreateVideoModerationTaskResponse.class);
        when(invoker.createVideoModerationTask(any(CreateVideoModerationTaskRequest.class)))
                .thenReturn(response);

        AsyncTaskRef ref = operation.submitAsync(videoRequest("data-req-1",
                ContentRef.url("https://cdn.example.com/video-001.mp4")));

        ArgumentCaptor<CreateVideoModerationTaskRequest> captor =
                ArgumentCaptor.forClass(CreateVideoModerationTaskRequest.class);
        verify(invoker).createVideoModerationTask(captor.capture());
        CreateVideoModerationTaskRequest sent = captor.getValue();
        assertThat(sent.getBizType()).isEqualTo("short_video");
        assertThat(sent.getCallbackUrl()).isEqualTo(CALLBACK_URL);
        assertThat(sent.getTasks()).hasSize(1);
        assertThat(sent.getTasks()[0].getDataId()).isEqualTo("data-req-1");
        assertThat(sent.getTasks()[0].getInput().getType()).isEqualTo("URL");
        assertThat(sent.getTasks()[0].getInput().getUrl()).isEqualTo("https://cdn.example.com/video-001.mp4");
        assertThat(ref.providerTaskId()).isEqualTo("t-video-001");
        assertThat(ref.dataId()).isEqualTo("data-req-1");
    }

    @Test
    void submitAsyncFailsOnNonOkCode() throws Exception {
        CreateVideoModerationTaskResponse response = new CreateVideoModerationTaskResponse();
        com.tencentcloudapi.vm.v20210922.models.TaskResult failed =
                new com.tencentcloudapi.vm.v20210922.models.TaskResult();
        failed.setCode("InvalidParameter");
        failed.setMessage("任务参数包含非法词，secret-token-abcdefgh12345678 应被脱敏");
        response.setResults(new com.tencentcloudapi.vm.v20210922.models.TaskResult[]{failed});
        when(invoker.createVideoModerationTask(any(CreateVideoModerationTaskRequest.class)))
                .thenReturn(response);

        assertThatThrownBy(() -> operation.submitAsync(videoRequest("data-1",
                ContentRef.url("https://example.com/v.mp4"))))
                .isInstanceOf(ProviderCallException.class)
                .hasMessageContaining("InvalidParameter")
                .hasMessageNotContaining("secret-token-abcdefgh12345678");
    }

    @Test
    void submitAsyncFailsWhenNoTaskResult() throws Exception {
        when(invoker.createVideoModerationTask(any(CreateVideoModerationTaskRequest.class)))
                .thenReturn(new CreateVideoModerationTaskResponse());
        assertThatThrownBy(() -> operation.submitAsync(videoRequest("data-1",
                ContentRef.url("https://example.com/v.mp4"))))
                .isInstanceOf(ProviderCallException.class)
                .hasMessageContaining("no task result");
    }

    @Test
    void queryInProgressWhenRunning() throws Exception {
        DescribeTaskDetailResponse response = load("video-running.json", DescribeTaskDetailResponse.class);
        when(invoker.describeTaskDetail(any(DescribeTaskDetailRequest.class))).thenReturn(response);

        AuditResult result = operation.query(new AsyncTaskRef("t-video-001", "data-req-1"));

        assertThat(result.ok()).isTrue();
        assertThat(result.finalResult()).isFalse();
        assertThat(result.suggestion()).isNull();
        assertThat(result.source()).isEqualTo(AuditSource.ASYNC_QUERY);
        assertThat(result.dataId()).isEqualTo("data-req-1");
        assertThat(result.raw()).containsEntry("status", "RUNNING");
    }

    @Test
    void queryFinishMapsLabelsAndSegmentsWithoutSensitivePayload() throws Exception {
        DescribeTaskDetailResponse response = load("video-finish.json", DescribeTaskDetailResponse.class);
        when(invoker.describeTaskDetail(any(DescribeTaskDetailRequest.class))).thenReturn(response);

        AuditResult result = operation.query(new AsyncTaskRef("t-video-001", "data-req-1"));

        assertThat(result.finalResult()).isTrue();
        assertThat(result.suggestion()).isEqualTo(RiskSuggestion.REJECT);
        assertThat(result.source()).isEqualTo(AuditSource.ASYNC_QUERY);
        // 任务级标签（Normal 段跳过）+ 音频命中段（HitFlag=1）+ 截帧命中段（HitFlag=1）
        assertThat(result.hits()).hasSize(3);
        assertThat(result.hits().get(0).labelPath()).isEqualTo("Porn/SexBehavior");
        assertThat(result.hits().get(0).confidence()).isEqualTo(98.0);
        assertThat(result.hits().get(1).labelPath()).isEqualTo("Abuse");
        assertThat(result.hits().get(1).raw()).containsEntry("offsetTime", "12");
        assertThat(result.hits().get(2).labelPath()).isEqualTo("Teenager");
        assertThat(result.hits().get(2).raw()).containsEntry("offsetTime", "5");
        // 脱敏红线：转写原文/片段地址/截帧地址/命中片段全部不透传
        String flattened = String.valueOf(result);
        assertThat(flattened).doesNotContain("加微信领福利");
        assertThat(flattened).doesNotContain("audio-clip-001.mp3");
        assertThat(flattened).doesNotContain("frame-001.jpg");
        assertThat(flattened).doesNotContain("seg-001.mp4");
        assertThat(flattened).doesNotContain("snippet-001.mp4");
        assertThat(flattened).doesNotContain("完整语音转写原文");
    }

    @Test
    void queryFailsOnErrorStatus() throws Exception {
        DescribeTaskDetailResponse response = load("video-error.json", DescribeTaskDetailResponse.class);
        when(invoker.describeTaskDetail(any(DescribeTaskDetailRequest.class))).thenReturn(response);

        assertThatThrownBy(() -> operation.query(new AsyncTaskRef("t-video-001", "data-req-1")))
                .isInstanceOf(ProviderCallException.class)
                .hasMessageContaining("ERROR")
                .hasMessageContaining("DecodeError");
    }

    @Test
    void queryRequiresProviderTaskId() {
        assertThatThrownBy(() -> operation.query(new AsyncTaskRef(" ", "data-1")))
                .isInstanceOf(InvalidRequestException.class);
    }

    @Test
    void handleCallbackAcceptsFinishPayload() throws Exception {
        DescribeTaskDetailResponse response = load("video-finish.json", DescribeTaskDetailResponse.class);
        // SDK 模型自带序列化，回调报文与查询响应同构
        String payload = AbstractModel.toJsonString(response);
        CallbackResult callback = operation.handleCallback(new CallbackRequest("tencent", java.util.Map.of(), payload));
        assertThat(callback.accepted()).isTrue();
        assertThat(callback.taskId()).isEqualTo("t-video-001");
        assertThat(callback.result().suggestion()).isEqualTo(RiskSuggestion.REJECT);
        assertThat(callback.result().source()).isEqualTo(AuditSource.ASYNC_CALLBACK);
        assertThat(callback.result().dataId()).isEqualTo("data-req-1");
    }

    @Test
    void handleCallbackRejectsInvalidAndNonTerminalPayloads() {
        assertThat(operation.handleCallback(new CallbackRequest("tencent", java.util.Map.of(), "not-json")))
                .extracting(CallbackResult::accepted).isEqualTo(false);
        assertThat(operation.handleCallback(new CallbackRequest("tencent", java.util.Map.of(), "")))
                .extracting(CallbackResult::accepted).isEqualTo(false);
        String running = "{\"TaskId\":\"t-video-001\",\"Status\":\"RUNNING\",\"RequestId\":\"r\"}";
        assertThat(operation.handleCallback(new CallbackRequest("tencent", java.util.Map.of(), running)))
                .extracting(CallbackResult::message).asString().contains("not terminal");
        String error = "{\"TaskId\":\"t-video-001\",\"Status\":\"ERROR\",\"ErrorType\":\"DecodeError\"}";
        assertThat(operation.handleCallback(new CallbackRequest("tencent", java.util.Map.of(), error)))
                .extracting(CallbackResult::message).asString().contains("ERROR");
    }

    @Test
    void handleCallbackFallsBackToDataIdWhenTaskIdMissing() throws Exception {
        DescribeTaskDetailResponse response = load("video-finish.json", DescribeTaskDetailResponse.class);
        response.setTaskId("");
        String payload = AbstractModel.toJsonString(response);
        CallbackResult callback = operation.handleCallback(new CallbackRequest("tencent", java.util.Map.of(), payload));
        assertThat(callback.accepted()).isTrue();
        assertThat(callback.taskId()).isEqualTo("data-req-1");
    }

    private AuditRequest videoRequest(String dataId, ContentRef content) {
        return AuditRequest.builder()
                .scene("short_video")
                .type(ContentType.VIDEO)
                .content(content)
                .dataId(dataId)
                .build();
    }

    private <T> T load(String fileName, Class<T> modelClass) {
        String json;
        try (InputStream input = getClass().getResourceAsStream(CONTRACT_DIR + fileName)) {
            assertThat(input).as("contract sample %s exists", fileName).isNotNull();
            json = new String(input.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("failed to load contract sample " + fileName, e);
        }
        return AbstractModel.fromJsonString(json, modelClass);
    }
}
