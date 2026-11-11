package io.github.wrqtec.content.safe.provider.shumei.internal;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;

import com.google.gson.Gson;
import io.github.wrqtec.content.safe.api.AuditResult;
import io.github.wrqtec.content.safe.api.enums.ConfidenceScale;
import io.github.wrqtec.content.safe.api.enums.RiskSuggestion;
import io.github.wrqtec.content.safe.model.ProviderPlan;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 契约测试：官方响应样本（contracts/shumei/）→ 统一结果
 *
 * <p>样本取自官方 api-doc 仓库 V4 文档示例（github.com/ishumei/api-doc，
 * 2026-11-06 调研），密钥与人名已打码。
 *
 * @author L.David
 */
class ShumeiAuditResultNormalizerContractTest {

    private static final Gson GSON = new Gson();

    private final ShumeiAuditResultNormalizer normalizer = new ShumeiAuditResultNormalizer();

    @Test
    void textPass() {
        ShumeiSyncResponse response = load("text-pass.json");
        AuditResult result = normalizer.normalize(response, context("text", "data-001"));

        assertThat(result.suggestion()).isEqualTo(RiskSuggestion.PASS);
        assertThat(result.ok()).isTrue();
        assertThat(result.finalResult()).isTrue();
        assertThat(result.hits()).isEmpty();
        assertThat(result.provider()).isEqualTo("shumei");
        assertThat(result.service()).isEqualTo("text");
        assertThat(result.dataId()).isEqualTo("data-001");
        assertThat(result.requestId()).isEqualTo("a6f1d3d5fa11fd02d226fb384968fe01");
    }

    @Test
    void textReview() {
        ShumeiSyncResponse response = load("text-review.json");
        AuditResult result = normalizer.normalize(response, context("text", null));

        assertThat(result.suggestion()).isEqualTo(RiskSuggestion.REVIEW);
        assertThat(result.hits()).hasSize(1);
        assertThat(result.hits().get(0).labelPath()).isEqualTo("ad/ad/ad");
        assertThat(result.hits().get(0).confidence()).isEqualTo(1.0D);
        assertThat(result.hits().get(0).confidenceScale()).isEqualTo(ConfidenceScale.ZERO_TO_ONE);
    }

    @Test
    void textRejectKeepsMatchedWordsAndDropsUserContent() {
        ShumeiSyncResponse response = load("text-reject.json");
        AuditResult result = normalizer.normalize(response, context("text", null));

        assertThat(result.suggestion()).isEqualTo(RiskSuggestion.REJECT);
        assertThat(result.hits()).hasSize(1);
        assertThat(result.hits().get(0).labelPath()).isEqualTo("ad/jiahaoyou/jiahaoyou");
        assertThat(result.hits().get(0).confidence()).isEqualTo(0.955D);
        assertThat(result.hits().get(0).raw()).containsEntry("riskSource", 1001);
        // 命中关键词进 hit.raw
        assertThat(result.hits().get(0).raw().get("matchedWords").toString()).contains("qq");
        // 用户内容原文（riskSegments 片段、filteredText、contactResult）整体不透传
        assertThat(result.toString()).doesNotContain("qq12345").doesNotContain("加个好友吧");
    }

    @Test
    void imagePass() {
        ShumeiSyncResponse response = load("image-pass.json");
        AuditResult result = normalizer.normalize(response, context("image", null));

        assertThat(result.suggestion()).isEqualTo(RiskSuggestion.PASS);
        assertThat(result.hits()).isEmpty();
    }

    @Test
    void imageReject() {
        ShumeiSyncResponse response = load("image-reject.json");
        AuditResult result = normalizer.normalize(response, context("image", null));

        assertThat(result.suggestion()).isEqualTo(RiskSuggestion.REJECT);
        assertThat(result.hits()).hasSize(1);
        assertThat(result.hits().get(0).labelPath()).isEqualTo("polity/politician/politician_face");
        assertThat(result.hits().get(0).confidence()).isEqualTo(0.988D);
        assertThat(result.hits().get(0).raw()).containsEntry("riskSource", 1002);
        // 人脸详情（含人名）不透传：raw 仅 riskLevel/riskSource/matchedWords
        assertThat(result.hits().get(0).raw().keySet()).containsExactlyInAnyOrder("riskLevel", "riskSource");
        assertThat(result.toString()).doesNotContain("face_num").doesNotContain("faces");
    }

    @Test
    void videoQueryRejectMapsFrameAndAudioHits() {
        ShumeiVideoQueryResponse response = loadVideo("video-query-reject.json");
        AuditResult result = normalizer.normalizeVideoQuery(response,
                ShumeiAuditResultNormalizer.context(ShumeiProviderPlanFixture.videoPlan("video"),
                        "data-req-1", Duration.ofMillis(12)));

        assertThat(result.suggestion()).isEqualTo(RiskSuggestion.REJECT);
        assertThat(result.finalResult()).isTrue();
        assertThat(result.source().name()).isEqualTo("ASYNC_QUERY");
        assertThat(result.dataId()).isEqualTo("data-req-1");
        assertThat(result.service()).isEqualTo("video");
        assertThat(result.hits()).hasSize(2);
        assertThat(result.hits().get(0).labelPath()).isEqualTo("polity/politician");
        assertThat(result.hits().get(0).confidence()).isEqualTo(0.97D);
        assertThat(result.hits().get(0).raw()).containsEntry("kind", "frame").containsEntry("riskSource", 1002);
        assertThat(result.hits().get(1).labelPath()).isEqualTo("ad");
        assertThat(result.hits().get(1).raw()).containsEntry("kind", "audio").containsEntry("riskSource", 1001);
        // 签名帧 URL / 音频 URL / 语音转写原文不透传
        assertThat(result.toString()).doesNotContain("signed.example.com").doesNotContain("语音转写原文");
    }

    @Test
    void videoCallbackMapsAsCallbackSource() {
        ShumeiVideoQueryResponse response = loadVideo("video-callback.json");
        AuditResult result = normalizer.normalizeVideoCallback(response,
                ShumeiAuditResultNormalizer.context(ShumeiProviderPlanFixture.videoPlan("video"),
                        "data-cb-001", Duration.ZERO));

        assertThat(result.source().name()).isEqualTo("ASYNC_CALLBACK");
        assertThat(result.suggestion()).isEqualTo(RiskSuggestion.REJECT);
        assertThat(result.hits()).hasSize(1);
        assertThat(result.hits().get(0).labelPath()).isEqualTo("porn");
        assertThat(result.requestId()).isEqualTo("req-video-cb-001");
    }

    @Test
    void errorQpsNotSuccess() {
        ShumeiSyncResponse response = load("error-qps.json");

        assertThat(response.isSuccess()).isFalse();
        assertThat(response.getCode()).isEqualTo(1901);
    }

    private static Map<String, Object> context(String eventId, String dataId) {
        ProviderPlan plan = ShumeiProviderPlanFixture.textPlan(eventId);
        return ShumeiAuditResultNormalizer.context(plan, dataId, Duration.ofMillis(12));
    }

    private static ShumeiSyncResponse load(String name) {
        try (Reader reader = new InputStreamReader(
                ShumeiAuditResultNormalizerContractTest.class
                        .getResourceAsStream("/contracts/shumei/" + name),
                StandardCharsets.UTF_8)) {
            return GSON.fromJson(reader, ShumeiSyncResponse.class);
        } catch (IOException e) {
            throw new IllegalStateException("load contract sample failed: " + name, e);
        }
    }

    private static ShumeiVideoQueryResponse loadVideo(String name) {
        try (Reader reader = new InputStreamReader(
                ShumeiAuditResultNormalizerContractTest.class
                        .getResourceAsStream("/contracts/shumei/" + name),
                StandardCharsets.UTF_8)) {
            return GSON.fromJson(reader, ShumeiVideoQueryResponse.class);
        } catch (IOException e) {
            throw new IllegalStateException("load contract sample failed: " + name, e);
        }
    }
}
