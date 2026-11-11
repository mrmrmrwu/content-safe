package io.github.wrqtec.content.safe.provider.aliyun.internal;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import com.aliyun.green20220302.models.DescribeImageModerationResultResponseBody;
import com.aliyun.green20220302.models.ImageModerationResponseBody;
import com.aliyun.green20220302.models.TextModerationResponseBody;
import com.aliyun.green20220302.models.VideoModerationResultResponseBody;
import com.aliyun.tea.TeaModel;
import com.google.gson.Gson;
import io.github.wrqtec.content.safe.api.AuditResult;
import io.github.wrqtec.content.safe.api.enums.RiskSuggestion;
import io.github.wrqtec.content.safe.model.ProviderPlan;
import io.github.wrqtec.content.safe.model.enums.AuditMode;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 契约测试：以 docs 声明的响应样本（src/test/resources/contracts/aliyun/）驱动 Normalizer，
 * 样本经 {@link TeaModel#build} 构造成真实 SDK 模型，验证归一化输出（PASS/REVIEW/REJECT、hits、
 * requestId、raw 脱敏）。禁止真实调用云接口（RULE §7）
 *
 * @author L.David
 */
class AliyunAuditResultNormalizerContractTest {

    private static final Gson GSON = new Gson();
    private static final AliyunAuditResultNormalizer NORMALIZER = new AliyunAuditResultNormalizer();

    private static final ProviderPlan TEXT_PLAN = new ProviderPlan(
            "aliyun", "green-cip", "2022-03-02", "TextModeration", "comment_detection",
            AuditMode.SYNC, AliyunLimits.textSync(), null);

    private static final ProviderPlan IMAGE_PLAN = new ProviderPlan(
            "aliyun", "green-cip", "2022-03-02", "ImageModeration", "baselineCheck",
            AuditMode.SYNC, AliyunLimits.imageSync(), null);

    private static final ProviderPlan VIDEO_PLAN = new ProviderPlan(
            "aliyun", "green-cip", "2022-03-02", "VideoModeration", "videoDetection",
            AuditMode.ASYNC_QUERY, AliyunLimits.videoModeration(), null);

    @Test
    void textPass() {
        TextModerationResponseBody body = buildText("text-pass.json");
        AuditResult result = NORMALIZER.normalize(body, context(TEXT_PLAN));
        assertThat(result.ok()).isTrue();
        assertThat(result.suggestion()).isEqualTo(RiskSuggestion.PASS);
        assertThat(result.pass()).isTrue();
        assertThat(result.hits()).isEmpty();
        assertThat(result.requestId()).isEqualTo("req-text-pass-001");
        assertThat(result.provider()).isEqualTo("aliyun");
        assertThat(result.service()).isEqualTo("comment_detection");
    }

    @Test
    void textReview() {
        TextModerationResponseBody body = buildText("text-review.json");
        AuditResult result = NORMALIZER.normalize(body, context(TEXT_PLAN));
        assertThat(result.suggestion()).isEqualTo(RiskSuggestion.REVIEW);
        assertThat(result.pass()).isFalse();
        assertThat(result.hits()).hasSize(1);
        assertThat(result.hits().get(0).labels()).containsExactly("ad_001");
        assertThat(result.requestId()).isEqualTo("req-text-review-001");
    }

    @Test
    void textReject() {
        TextModerationResponseBody body = buildText("text-reject.json");
        AuditResult result = NORMALIZER.normalize(body, context(TEXT_PLAN));
        assertThat(result.suggestion()).isEqualTo(RiskSuggestion.REJECT);
        assertThat(result.hits()).hasSize(1);
        assertThat(result.hits().get(0).labels())
                .containsExactly("porn_001", "contraband_002");
        assertThat(result.hits().get(0).labelPath()).isEqualTo("porn_001/contraband_002");
        assertThat(result.hits().get(0).confidence()).isEqualTo(98.5D);
    }

    @Test
    void imagePass() {
        ImageModerationResponseBody body = buildImage("image-pass.json");
        AuditResult result = NORMALIZER.normalize(body, context(IMAGE_PLAN));
        assertThat(result.suggestion()).isEqualTo(RiskSuggestion.PASS);
        assertThat(result.pass()).isTrue();
        assertThat(result.hits()).isEmpty();
        assertThat(result.requestId()).isEqualTo("req-image-pass-001");
    }

    @Test
    void imageReject() {
        ImageModerationResponseBody body = buildImage("image-reject.json");
        AuditResult result = NORMALIZER.normalize(body, context(IMAGE_PLAN));
        assertThat(result.suggestion()).isEqualTo(RiskSuggestion.REJECT);
        assertThat(result.hits()).hasSize(1);
        assertThat(result.hits().get(0).labelPath()).isEqualTo("porn");
        assertThat(result.hits().get(0).confidence()).isEqualTo(92.5D);
        assertThat(result.requestId()).isEqualTo("req-image-reject-001");
    }

    @Test
    void videoPass() {
        VideoModerationResultResponseBody body = buildVideo("video-pass.json");
        AuditResult result = NORMALIZER.normalizeVideoAsyncResult(body, context(VIDEO_PLAN));
        assertThat(result.ok()).isTrue();
        assertThat(result.suggestion()).isEqualTo(RiskSuggestion.PASS);
        assertThat(result.pass()).isTrue();
        assertThat(result.hits()).isEmpty();
        assertThat(result.requestId()).isEqualTo("req-video-pass-001");
    }

    @Test
    void videoReject() {
        VideoModerationResultResponseBody body = buildVideo("video-reject.json");
        AuditResult result = NORMALIZER.normalizeVideoAsyncResult(body, context(VIDEO_PLAN));
        assertThat(result.suggestion()).isEqualTo(RiskSuggestion.REJECT);
        assertThat(result.pass()).isFalse();
        // 帧命中 + 语音命中各一条
        assertThat(result.hits()).hasSize(2);
        assertThat(result.requestId()).isEqualTo("req-video-reject-001");
        // 截帧图 URL 与语音转写原文不得进入归一化结果（铁律 §5.6）
        assertThat(String.valueOf(result)).doesNotContain("oss.example.com").doesNotContain("加微信");
    }

    @Test
    void imageAsyncCallbackContentNormalizesLikeQueryData() {
        // 回调 Content（camelCase JSON，官方 2638108）与查询 Data 同构；报文无顶层 code/requestId。
        // 生产路径（handleCallback）与测试一致用 Gson 反序列化：TeaModel.toModel 只认
        // @NameInMap PascalCase 键，camelCase Content 嵌套元素会全部落空
        DescribeImageModerationResultResponseBody.DescribeImageModerationResultResponseBodyData data =
                GSON.fromJson(loadJsonAsString("image-callback-content.json"),
                        DescribeImageModerationResultResponseBody
                                .DescribeImageModerationResultResponseBodyData.class);
        AuditResult result = NORMALIZER.normalizeImageAsyncCallbackData(data, context(IMAGE_PLAN));
        assertThat(result.suggestion()).isEqualTo(RiskSuggestion.REJECT);
        assertThat(result.hits()).hasSize(1);
        assertThat(result.hits().get(0).labelPath()).isEqualTo("porn");
        assertThat(result.dataId()).isEqualTo("data-1");
        assertThat(result.raw()).doesNotContainKeys("code", "requestId");
        // 截图 URL（frame 字段内）不透传
        assertThat(String.valueOf(result)).doesNotContain("oss.example.com");
    }

    private static Map<String, Object> context(ProviderPlan plan) {
        return AliyunAuditResultNormalizer.context(plan, "data-1", java.time.Duration.ofMillis(12));
    }

    @SuppressWarnings("unchecked")
    private static TextModerationResponseBody buildText(String fileName) {
        try {
            return TeaModel.build(loadJson(fileName), new TextModerationResponseBody());
        } catch (Exception e) {
            throw new IllegalStateException("failed to build text contract sample " + fileName, e);
        }
    }

    @SuppressWarnings("unchecked")
    private static ImageModerationResponseBody buildImage(String fileName) {
        try {
            return TeaModel.build(loadJson(fileName), new ImageModerationResponseBody());
        } catch (Exception e) {
            throw new IllegalStateException("failed to build image contract sample " + fileName, e);
        }
    }

    @SuppressWarnings("unchecked")
    private static VideoModerationResultResponseBody buildVideo(String fileName) {
        try {
            return TeaModel.build(loadJson(fileName), new VideoModerationResultResponseBody());
        } catch (Exception e) {
            throw new IllegalStateException("failed to build video contract sample " + fileName, e);
        }
    }

    static Map<String, Object> loadJson(String fileName) {
        return GSON.fromJson(loadJsonAsString(fileName), Map.class);
    }

    static String loadJsonAsString(String fileName) {
        String path = "/contracts/aliyun/" + fileName;
        try (var inputStream = AliyunAuditResultNormalizerContractTest.class.getResourceAsStream(path)) {
            assertThat(inputStream).as("contract sample %s must exist", path).isNotNull();
            return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
