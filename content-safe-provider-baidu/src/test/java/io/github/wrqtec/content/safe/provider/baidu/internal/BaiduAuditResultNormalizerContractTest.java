package io.github.wrqtec.content.safe.provider.baidu.internal;

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
import io.github.wrqtec.content.safe.provider.baidu.BaiduAuditOperationProvider;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 契约测试：真实响应样本（contracts/baidu/）→ 统一结果
 *
 * @author L.David
 */
class BaiduAuditResultNormalizerContractTest {

    private static final Gson GSON = new Gson();

    private final BaiduAuditResultNormalizer normalizer = new BaiduAuditResultNormalizer();

    @Test
    void textPass() {
        BaiduCensorResponse response = load("text-pass.json");
        AuditResult result = normalizer.normalize(response, context("default", "data-001"));

        assertThat(result.suggestion()).isEqualTo(RiskSuggestion.PASS);
        assertThat(result.ok()).isTrue();
        assertThat(result.hits()).isEmpty();
        assertThat(result.provider()).isEqualTo("baidu");
        assertThat(result.service()).isEqualTo("default");
        assertThat(result.dataId()).isEqualTo("data-001");
        assertThat(result.requestId()).isEqualTo("1916000000000000001");
    }

    @Test
    void textReview() {
        BaiduCensorResponse response = load("text-review.json");
        AuditResult result = normalizer.normalize(response, context("101", null));

        assertThat(result.suggestion()).isEqualTo(RiskSuggestion.REVIEW);
        assertThat(result.hits()).hasSize(1);
        assertThat(result.hits().get(0).labelPath()).isEqualTo("12.1");
        assertThat(result.hits().get(0).labels()).containsExactly("存在广告信息");
        assertThat(result.hits().get(0).confidence()).isEqualTo(0.72D);
        assertThat(result.hits().get(0).confidenceScale()).isEqualTo(ConfidenceScale.ZERO_TO_ONE);
        assertThat(result.hits().get(0).evidence()).isEqualTo("低价促销");
    }

    @Test
    void textReject() {
        BaiduCensorResponse response = load("text-reject.json");
        AuditResult result = normalizer.normalize(response, context("default", null));

        assertThat(result.suggestion()).isEqualTo(RiskSuggestion.REJECT);
        assertThat(result.hits()).hasSize(1);
        assertThat(result.hits().get(0).labelPath()).isEqualTo("1.2");
        assertThat(result.hits().get(0).evidence()).isEqualTo("敏感词甲,敏感词乙");
    }

    @Test
    void imagePass() {
        BaiduCensorResponse response = load("image-pass.json");
        AuditResult result = normalizer.normalize(response, context("default", null));

        assertThat(result.suggestion()).isEqualTo(RiskSuggestion.PASS);
        assertThat(result.hits()).isEmpty();
    }

    @Test
    void imageReject() {
        BaiduCensorResponse response = load("image-reject.json");
        AuditResult result = normalizer.normalize(response, context("default", null));

        assertThat(result.suggestion()).isEqualTo(RiskSuggestion.REJECT);
        assertThat(result.hits()).hasSize(1);
        assertThat(result.hits().get(0).labelPath()).isEqualTo("2.0");
        assertThat(result.hits().get(0).confidence()).isEqualTo(0.96D);
        assertThat(result.hits().get(0).evidence()).isNull();
    }

    @Test
    void errorTokenExpired() {
        BaiduCensorResponse response = load("error-token-expired.json");

        assertThat(response.isSuccess()).isFalse();
        assertThat(response.getErrorCode()).isEqualTo(111);
        assertThat(BaiduErrorMapper.isTokenError(response.getErrorCode())).isTrue();
    }

    @Test
    void videoPullRejectMapsFramesAndAudios() {
        BaiduVideoPullResponse response = loadVideo("video-pull-reject.json", BaiduVideoPullResponse.class);
        AuditResult result = normalizer.normalizeVideoPull(response.getData(), response.getLogId(),
                videoContext());

        assertThat(result.suggestion()).isEqualTo(RiskSuggestion.REJECT);
        assertThat(result.finalResult()).isTrue();
        assertThat(result.source()).isEqualTo(io.github.wrqtec.content.safe.api.enums.AuditSource.ASYNC_QUERY);
        assertThat(result.requestId()).isEqualTo("123003");
        assertThat(result.hits()).hasSize(2);
        assertThat(result.hits().get(0).labelPath()).isEqualTo("43.430101");
        assertThat(result.hits().get(0).evidence()).isEqualTo("涉黄命中词zz9900112233");
        assertThat(result.hits().get(0).raw()).containsEntry("frameTimeStamp", 12L)
                .containsEntry("datasetName", "默认涉黄库");
        assertThat(result.hits().get(1).labelPath()).isEqualTo("24");
        assertThat(result.hits().get(1).raw()).containsEntry("startTime", 5000)
                .containsEntry("endTime", 9000);

        String flattened = String.valueOf(result);
        assertThat(flattened)
                .doesNotContain("bos-sign.example.com")
                .doesNotContain("这段语音转写原文不应透传到统一结果");
    }

    @Test
    void videoCallbackMapsDataIdFromExtId() {
        BaiduVideoCallbackPayload payload = loadVideo("video-callback.json", BaiduVideoCallbackPayload.class);
        AuditResult result = normalizer.normalizeVideoCallback(payload.getResult(),
                videoContext());

        assertThat(result.suggestion()).isEqualTo(RiskSuggestion.REJECT);
        assertThat(result.source()).isEqualTo(io.github.wrqtec.content.safe.api.enums.AuditSource.ASYNC_CALLBACK);
        assertThat(result.hits()).hasSize(1);
        assertThat(result.hits().get(0).labelPath()).isEqualTo("43.430101");
        assertThat(String.valueOf(result)).doesNotContain("bos-sign.example.com");
    }

    private static Map<String, Object> videoContext() {
        return BaiduAuditResultNormalizer.context(BaiduAuditOperationProvider.videoPlan("303"),
                "data-cb-001", Duration.ofMillis(12));
    }

    private static <T> T loadVideo(String name, Class<T> type) {
        try (Reader reader = new InputStreamReader(
                BaiduAuditResultNormalizerContractTest.class
                        .getResourceAsStream("/contracts/baidu/" + name),
                StandardCharsets.UTF_8)) {
            return GSON.fromJson(reader, type);
        } catch (IOException e) {
            throw new IllegalStateException("load contract sample failed: " + name, e);
        }
    }

    private static Map<String, Object> context(String strategyId, String dataId) {
        ProviderPlan plan = BaiduProviderPlanFixture.textPlan(strategyId);
        return BaiduAuditResultNormalizer.context(plan, dataId, Duration.ofMillis(12));
    }

    private static BaiduCensorResponse load(String name) {
        try (Reader reader = new InputStreamReader(
                BaiduAuditResultNormalizerContractTest.class
                        .getResourceAsStream("/contracts/baidu/" + name),
                StandardCharsets.UTF_8)) {
            return GSON.fromJson(reader, BaiduCensorResponse.class);
        } catch (IOException e) {
            throw new IllegalStateException("load contract sample failed: " + name, e);
        }
    }
}
