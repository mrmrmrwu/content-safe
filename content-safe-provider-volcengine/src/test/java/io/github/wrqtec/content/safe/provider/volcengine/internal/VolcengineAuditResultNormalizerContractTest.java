package io.github.wrqtec.content.safe.provider.volcengine.internal;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;

import com.google.gson.Gson;
import com.volcengine.model.response.ImageRiskDetectionResponse;
import com.volcengine.model.response.TextRiskResponse;
import com.volcengine.model.response.VideoRiskResultResponse;
import io.github.wrqtec.content.safe.api.AuditResult;
import io.github.wrqtec.content.safe.api.RiskHit;
import io.github.wrqtec.content.safe.api.enums.AuditSource;
import io.github.wrqtec.content.safe.api.enums.RiskSuggestion;
import io.github.wrqtec.content.safe.model.ProviderPlan;
import io.github.wrqtec.content.safe.provider.volcengine.VolcengineAuditOperationProvider;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 火山引擎契约测试：以真实响应形态的 JSON 样本（camelCase 键，与
 * volc-sdk-java 模型字段一致，Gson 按字段名反序列化）驱动 SDK 模型还原，
 * 验证归一化输出。样本位于 contracts/volcengine/
 *
 * @author L.David
 */
class VolcengineAuditResultNormalizerContractTest {

    private static final String CONTRACT_DIR = "/contracts/volcengine/";

    private final Gson gson = new Gson();
    private final VolcengineAuditResultNormalizer normalizer = new VolcengineAuditResultNormalizer();

    private final ProviderPlan textPlan = VolcengineAuditOperationProvider.syncPlan(
            "TextRisk", "svc-nickname", "2022-01-26", VolcengineLimits.textRisk());

    private final ProviderPlan imagePlan = VolcengineAuditOperationProvider.syncPlan(
            "ImageContentRiskV2", "svc-avatar", "2021-11-29", VolcengineLimits.imageContentRisk());

    private final ProviderPlan videoPlan = VolcengineAuditOperationProvider.videoPlan("svc-short-video");

    @Test
    void textPass() {
        TextRiskResponse response = load("text-pass.json", TextRiskResponse.class);
        AuditResult result = normalizer.normalize(response,
                VolcengineAuditResultNormalizer.context(textPlan, "d-001", Duration.ofMillis(12)));
        assertThat(result.ok()).isTrue();
        assertThat(result.suggestion()).isEqualTo(RiskSuggestion.PASS);
        assertThat(result.pass()).isTrue();
        assertThat(result.hits()).isEmpty();
        assertThat(result.provider()).isEqualTo("volcengine");
        assertThat(result.service()).isEqualTo("svc-nickname");
        assertThat(result.requestId()).isEqualTo("req-text-pass-001");
        assertThat(result.raw()).containsEntry("Service", "svc-nickname");
    }

    @Test
    void textReview() {
        TextRiskResponse response = load("text-review.json", TextRiskResponse.class);
        AuditResult result = normalizer.normalize(response,
                VolcengineAuditResultNormalizer.context(textPlan, "d-002", Duration.ofMillis(15)));
        assertThat(result.suggestion()).isEqualTo(RiskSuggestion.REVIEW);
        assertThat(result.hits()).hasSize(1);
        RiskHit hit = result.hits().get(0);
        assertThat(hit.labelPath()).isEqualTo("abuse/insult");
        // 命中词是用户内容片段：只进 hit.raw，不进 labels/evidence
        assertThat(hit.labels()).doesNotContain("命中词甲");
        assertThat(hit.evidence()).isNull();
        assertThat(hit.raw()).containsEntry("libName", "default");
        assertThat(String.valueOf(hit.raw().get("matchedWords"))).contains("命中词甲");
    }

    @Test
    void textReject() {
        TextRiskResponse response = load("text-reject.json", TextRiskResponse.class);
        AuditResult result = normalizer.normalize(response,
                VolcengineAuditResultNormalizer.context(textPlan, "d-003", Duration.ofMillis(9)));
        assertThat(result.suggestion()).isEqualTo(RiskSuggestion.REJECT);
        assertThat(result.hits()).hasSize(1);
        assertThat(result.hits().get(0).labelPath()).isEqualTo("porn/adult");
    }

    @Test
    void imagePass() {
        ImageRiskDetectionResponse response = load("image-pass.json", ImageRiskDetectionResponse.class);
        AuditResult result = normalizer.normalize(response,
                VolcengineAuditResultNormalizer.context(imagePlan, null, Duration.ofMillis(20)));
        assertThat(result.suggestion()).isEqualTo(RiskSuggestion.PASS);
        assertThat(result.hits()).isEmpty();
        assertThat(result.dataId()).isNull();
    }

    @Test
    void imageReview() {
        ImageRiskDetectionResponse response = load("image-review.json", ImageRiskDetectionResponse.class);
        AuditResult result = normalizer.normalize(response,
                VolcengineAuditResultNormalizer.context(imagePlan, "d-004", Duration.ofMillis(25)));
        assertThat(result.suggestion()).isEqualTo(RiskSuggestion.REVIEW);
        assertThat(result.hits()).hasSize(1);
        RiskHit hit = result.hits().get(0);
        assertThat(hit.labelPath()).isEqualTo("sexy/female_hot");
        // OCR 文本原文与帧签名 URL（用户内容/签名 URL）绝不进入归一化结果
        String serialized = hit.raw().toString() + hit.evidence() + hit.labelPath() + result.raw().toString();
        assertThat(serialized).doesNotContain("hello world");
        assertThat(serialized).doesNotContain("signed-frame");
    }

    @Test
    void imageReject() {
        ImageRiskDetectionResponse response = load("image-reject.json", ImageRiskDetectionResponse.class);
        AuditResult result = normalizer.normalize(response,
                VolcengineAuditResultNormalizer.context(imagePlan, "d-005", Duration.ofMillis(18)));
        assertThat(result.suggestion()).isEqualTo(RiskSuggestion.REJECT);
        assertThat(result.hits()).hasSize(1);
        RiskHit hit = result.hits().get(0);
        assertThat(hit.labelPath()).isEqualTo("porn/male_hot");
        Map<?, ?> raw = hit.raw();
        assertThat(String.valueOf(raw.get("details"))).contains("porn_region_1", "porn_region_2");
        String serialized = raw.toString() + hit.evidence();
        assertThat(serialized).doesNotContain("违规文字图片");
    }

    @Test
    void videoRejectMapsFrameAndAudioHitsWithoutSensitivePayload() {
        VideoRiskResultResponse response = load("video-finish.json", VideoRiskResultResponse.class);
        AuditResult result = normalizer.normalize(response,
                VolcengineAuditResultNormalizer.context(videoPlan, "d-video-001", Duration.ofMillis(30)));
        assertThat(result.suggestion()).isEqualTo(RiskSuggestion.REJECT);
        assertThat(result.ok()).isTrue();
        assertThat(result.finalResult()).isTrue();
        assertThat(result.requestId()).isEqualTo("req-video-finish-001");
        // 视频走异步查询链路，终态来源必须标 ASYNC_QUERY 而非 SYNC
        assertThat(result.source()).isEqualTo(AuditSource.ASYNC_QUERY);

        // 帧命中：合规帧（pass）跳过；offset/sliceId/明细进 hit.raw
        assertThat(result.hits()).hasSize(2);
        RiskHit frameHit = result.hits().get(0);
        assertThat(frameHit.labelPath()).isEqualTo("porn/sexy");
        Map<?, ?> frameRaw = frameHit.raw();
        assertThat(frameRaw.get("offset")).isEqualTo(12.5f);
        assertThat(frameRaw.get("sliceId")).isEqualTo("slice-001");
        assertThat(frameRaw.get("frameDecision")).isEqualTo("reject");
        assertThat(String.valueOf(frameRaw.get("detail"))).contains("porn_region_1");

        // 语音命中：matchedWords 只进 hit.raw
        RiskHit audioHit = result.hits().get(1);
        assertThat(audioHit.labelPath()).isEqualTo("ad/contact");
        Map<?, ?> audioRaw = audioHit.raw();
        assertThat(audioRaw.get("startTime")).isEqualTo(5000);
        assertThat(audioRaw.get("endTime")).isEqualTo(9000);
        assertThat(String.valueOf(audioRaw.get("matchedWords"))).contains("qq12345");

        // 帧签名 URL、帧内文字、语音转写原文（用户内容/签名 URL）绝不进入归一化结果
        String serialized = String.valueOf(result);
        assertThat(serialized).doesNotContain("signed.example.com");
        assertThat(serialized).doesNotContain("zz00112233");
        assertThat(serialized).doesNotContain("不应透传");
    }

    private <T> T load(String fileName, Class<T> modelClass) {
        String json;
        try (InputStream input = getClass().getResourceAsStream(CONTRACT_DIR + fileName)) {
            assertThat(input).as("contract sample %s exists", fileName).isNotNull();
            json = new String(input.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("failed to load contract sample " + fileName, e);
        }
        return gson.fromJson(json, modelClass);
    }
}
