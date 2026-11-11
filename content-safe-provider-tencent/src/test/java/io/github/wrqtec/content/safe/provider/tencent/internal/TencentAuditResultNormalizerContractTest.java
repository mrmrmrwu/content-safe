package io.github.wrqtec.content.safe.provider.tencent.internal;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;

import com.tencentcloudapi.common.AbstractModel;
import com.tencentcloudapi.ims.v20201229.models.ImageModerationResponse;
import com.tencentcloudapi.tms.v20201229.models.TextModerationResponse;
import com.tencentcloudapi.vm.v20210922.models.DescribeTaskDetailResponse;
import io.github.wrqtec.content.safe.api.AuditResult;
import io.github.wrqtec.content.safe.api.RiskHit;
import io.github.wrqtec.content.safe.api.enums.AuditSource;
import io.github.wrqtec.content.safe.api.enums.ConfidenceScale;
import io.github.wrqtec.content.safe.api.enums.RiskSuggestion;
import io.github.wrqtec.content.safe.model.OperationLimits;
import io.github.wrqtec.content.safe.model.ProviderPlan;
import io.github.wrqtec.content.safe.provider.tencent.TencentAuditOperationProvider;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 腾讯云契约测试：以真实响应形态的 JSON 样本（PascalCase 键，与腾讯云
 * API 一致）驱动 {@link AbstractModel#fromJsonString} 还原 SDK 模型，
 * 验证归一化输出。样本位于 contracts/tencent/
 *
 * @author L.David
 */
class TencentAuditResultNormalizerContractTest {

    private static final String CONTRACT_DIR = "/contracts/tencent/";

    private final TencentAuditResultNormalizer normalizer = new TencentAuditResultNormalizer();

    private final ProviderPlan textPlan = TencentAuditOperationProvider.syncPlan(
            "TextModeration", "comment", new OperationLimits(null, 1000.0, Duration.ofSeconds(10),
                    10000, 0, false));

    private final ProviderPlan imagePlan = TencentAuditOperationProvider.syncPlan(
            "ImageModeration", "ugc-image", new OperationLimits(null, 100.0, Duration.ofSeconds(10),
                    10485760, 0, false));

    private final ProviderPlan videoPlan = TencentAuditOperationProvider.videoPlan("short_video");

    @Test
    void textPass() {
        TextModerationResponse response = load("text-pass.json", TextModerationResponse.class);
        AuditResult result = normalizer.normalize(response,
                TencentAuditResultNormalizer.context(textPlan, "d-001", Duration.ofMillis(12)));
        assertThat(result.ok()).isTrue();
        assertThat(result.suggestion()).isEqualTo(RiskSuggestion.PASS);
        assertThat(result.pass()).isTrue();
        assertThat(result.hits()).isEmpty();
        assertThat(result.provider()).isEqualTo("tencent");
        assertThat(result.service()).isEqualTo("comment");
        assertThat(result.requestId()).isEqualTo("req-text-pass");
    }

    @Test
    void textReview() {
        TextModerationResponse response = load("text-review.json", TextModerationResponse.class);
        AuditResult result = normalizer.normalize(response,
                TencentAuditResultNormalizer.context(textPlan, "d-002", Duration.ofMillis(15)));
        assertThat(result.suggestion()).isEqualTo(RiskSuggestion.REVIEW);
        assertThat(result.hits()).hasSize(1);
        RiskHit hit = result.hits().get(0);
        assertThat(hit.labelPath()).isEqualTo("Abuse/AbuseMedium");
        assertThat(hit.confidence()).isEqualTo(55.0);
        assertThat(hit.confidenceScale()).isEqualTo(ConfidenceScale.PERCENTAGE);
        // Keywords 是用户内容片段：只能进 hit.raw，不进 labels/evidence
        assertThat(hit.labels()).doesNotContain("敏感词甲");
        assertThat(hit.evidence()).isNull();
        assertThat(hit.raw()).containsKey("keywords");
    }

    @Test
    void textReject() {
        TextModerationResponse response = load("text-reject.json", TextModerationResponse.class);
        AuditResult result = normalizer.normalize(response,
                TencentAuditResultNormalizer.context(textPlan, "d-003", Duration.ofMillis(9)));
        assertThat(result.suggestion()).isEqualTo(RiskSuggestion.REJECT);
        assertThat(result.hits()).hasSize(1);
        assertThat(result.hits().get(0).labelPath()).isEqualTo("Porn/PornHigh");
        assertThat(result.hits().get(0).confidence()).isEqualTo(95.0);
    }

    @Test
    void imagePass() {
        ImageModerationResponse response = load("image-pass.json", ImageModerationResponse.class);
        AuditResult result = normalizer.normalize(response,
                TencentAuditResultNormalizer.context(imagePlan, null, Duration.ofMillis(20)));
        assertThat(result.suggestion()).isEqualTo(RiskSuggestion.PASS);
        assertThat(result.hits()).isEmpty();
    }

    @Test
    void imageReview() {
        ImageModerationResponse response = load("image-review.json", ImageModerationResponse.class);
        AuditResult result = normalizer.normalize(response,
                TencentAuditResultNormalizer.context(imagePlan, null, Duration.ofMillis(25)));
        assertThat(result.suggestion()).isEqualTo(RiskSuggestion.REVIEW);
        assertThat(result.hits()).hasSize(1);
        RiskHit hit = result.hits().get(0);
        assertThat(hit.labelPath()).isEqualTo("Porn/SexyBehavior");
        assertThat(hit.confidence()).isEqualTo(60.0);
        // OCR 文本原文（用户内容）绝不进入归一化结果
        String serialized = hit.raw().toString() + hit.evidence() + hit.labelPath();
        assertThat(serialized).doesNotContain("hello world");
    }

    @Test
    void imageReject() {
        ImageModerationResponse response = load("image-reject.json", ImageModerationResponse.class);
        AuditResult result = normalizer.normalize(response,
                TencentAuditResultNormalizer.context(imagePlan, null, Duration.ofMillis(18)));
        assertThat(result.suggestion()).isEqualTo(RiskSuggestion.REJECT);
        assertThat(result.hits()).hasSize(1);
        RiskHit hit = result.hits().get(0);
        assertThat(hit.labelPath()).isEqualTo("Terrorism/Weapon/Gun");
        assertThat(hit.confidence()).isEqualTo(95.0);
        assertThat(hit.raw()).containsKey("details");
    }

    @Test
    void videoQueryFinish() {
        DescribeTaskDetailResponse response = load("video-finish.json", DescribeTaskDetailResponse.class);
        AuditResult result = normalizer.normalizeVideoQuery(response,
                TencentAuditResultNormalizer.context(videoPlan, "data-req-1", Duration.ofMillis(30)));
        assertThat(result.finalResult()).isTrue();
        assertThat(result.suggestion()).isEqualTo(RiskSuggestion.REJECT);
        assertThat(result.source()).isEqualTo(AuditSource.ASYNC_QUERY);
        assertThat(result.provider()).isEqualTo("tencent");
        assertThat(result.service()).isEqualTo("short_video");
        assertThat(result.dataId()).isEqualTo("data-req-1");
        assertThat(result.requestId()).isEqualTo("req-query-001");
        // 任务级标签（Normal 跳过）+ 音频命中段 + 截帧命中段
        assertThat(result.hits()).hasSize(3);
        assertThat(result.hits().get(0).labelPath()).isEqualTo("Porn/SexBehavior");
        assertThat(result.hits().get(0).confidence()).isEqualTo(98.0);
        assertThat(result.hits().get(0).confidenceScale()).isEqualTo(ConfidenceScale.PERCENTAGE);
        assertThat(result.hits().get(1).raw()).containsEntry("offsetTime", "12");
        assertThat(result.hits().get(2).labelPath()).isEqualTo("Teenager");
        // 脱敏红线：转写原文/片段地址/截帧地址/命中片段全部不透传
        String serialized = String.valueOf(result);
        assertThat(serialized).doesNotContain("加微信领福利");
        assertThat(serialized).doesNotContain("audio-clip-001.mp3");
        assertThat(serialized).doesNotContain("frame-001.jpg");
        assertThat(serialized).doesNotContain("seg-001.mp4");
        assertThat(serialized).doesNotContain("snippet-001.mp4");
        assertThat(serialized).doesNotContain("完整语音转写原文");
    }

    @Test
    void videoQueryInProgressKeepsTaskOpen() {
        DescribeTaskDetailResponse response = load("video-running.json", DescribeTaskDetailResponse.class);
        AuditResult result = normalizer.inProgressVideo(response, "RUNNING",
                TencentAuditResultNormalizer.context(videoPlan, "data-req-1", Duration.ofMillis(8)));
        assertThat(result.ok()).isTrue();
        assertThat(result.finalResult()).isFalse();
        assertThat(result.suggestion()).isNull();
        assertThat(result.source()).isEqualTo(AuditSource.ASYNC_QUERY);
        assertThat(result.raw()).containsEntry("status", "RUNNING");
        assertThat(result.requestId()).isEqualTo("req-query-002");
    }

    @Test
    void videoCallbackUsesAsyncCallbackSource() {
        DescribeTaskDetailResponse response = load("video-finish.json", DescribeTaskDetailResponse.class);
        AuditResult result = normalizer.normalizeVideoCallback(response,
                TencentAuditResultNormalizer.context(videoPlan, "data-req-1", Duration.ZERO));
        assertThat(result.finalResult()).isTrue();
        assertThat(result.suggestion()).isEqualTo(RiskSuggestion.REJECT);
        assertThat(result.source()).isEqualTo(AuditSource.ASYNC_CALLBACK);
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
