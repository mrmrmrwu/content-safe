package io.github.wrqtec.content.safe.provider.aliyun.internal;

import java.time.Duration;
import java.util.Map;

import com.aliyun.green20220302.models.ImageModerationResponseBody;
import com.aliyun.green20220302.models.TextModerationResponseBody;
import io.github.wrqtec.content.safe.api.AuditResult;
import io.github.wrqtec.content.safe.api.enums.ConfidenceScale;
import io.github.wrqtec.content.safe.api.enums.RiskSuggestion;
import io.github.wrqtec.content.safe.api.exception.ProviderCallException;
import io.github.wrqtec.content.safe.model.ProviderPlan;
import io.github.wrqtec.content.safe.model.enums.AuditMode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link AliyunAuditResultNormalizer} 单元测试：三档映射、回退规则、边界输入
 *
 * @author L.David
 */
class AliyunAuditResultNormalizerTest {

    private static final AliyunAuditResultNormalizer NORMALIZER = new AliyunAuditResultNormalizer();

    private static final ProviderPlan PLAN = new ProviderPlan(
            "aliyun", "green-cip", "2022-03-02", "TextModeration", "comment_detection",
            AuditMode.SYNC, AliyunLimits.textSync(), null);

    @ParameterizedTest
    @CsvSource({
            "none, PASS",
            "low, PASS",
            "medium, REVIEW",
            "high, REJECT",
            "NONE, PASS",
            "High, REJECT"
    })
    void riskLevelMapping(String riskLevel, RiskSuggestion expected) {
        assertThat(AliyunAuditResultNormalizer.fromRiskLevel(riskLevel)).isEqualTo(expected);
    }

    @Test
    void riskLevelUnknownOrNullFallsBack() {
        assertThat(AliyunAuditResultNormalizer.fromRiskLevel("unknown")).isNull();
        assertThat(AliyunAuditResultNormalizer.fromRiskLevel(null)).isNull();
        assertThat(AliyunAuditResultNormalizer.fromRiskLevel("  ")).isNull();
    }

    @Test
    void textNullDataTreatedAsPass() {
        TextModerationResponseBody body = new TextModerationResponseBody()
                .setCode(200).setRequestId("r-1");
        AuditResult result = NORMALIZER.normalize(body, context());
        assertThat(result.suggestion()).isEqualTo(RiskSuggestion.PASS);
        assertThat(result.hits()).isEmpty();
    }

    @Test
    void textFallbackToLabelsWhenRiskLevelMissing() {
        TextModerationResponseBody body = new TextModerationResponseBody()
                .setCode(200).setRequestId("r-2")
                .setData(new TextModerationResponseBody.TextModerationResponseBodyData()
                        .setLabels("ad_001").setReason("{}"));
        AuditResult result = NORMALIZER.normalize(body, context());
        assertThat(result.suggestion()).isEqualTo(RiskSuggestion.REJECT);
        assertThat(result.hits()).hasSize(1);
    }

    @Test
    void imageNullDataTreatedAsPass() {
        ImageModerationResponseBody body = new ImageModerationResponseBody()
                .setCode(200).setRequestId("r-3");
        AuditResult result = NORMALIZER.normalize(body, context());
        assertThat(result.suggestion()).isEqualTo(RiskSuggestion.PASS);
    }

    @Test
    void imageFallbackToHitsWhenRiskLevelMissing() {
        ImageModerationResponseBody body = new ImageModerationResponseBody()
                .setCode(200).setRequestId("r-4")
                .setData(new ImageModerationResponseBody.ImageModerationResponseBodyData()
                        .setResult(java.util.List.of(
                                new ImageModerationResponseBody.ImageModerationResponseBodyDataResult()
                                        .setLabel("porn").setConfidence(80F))));
        AuditResult result = NORMALIZER.normalize(body, context());
        assertThat(result.suggestion()).isEqualTo(RiskSuggestion.REJECT);
        assertThat(result.hits()).hasSize(1);
    }

    @Test
    void unknownRawTypeRejected() {
        assertThatThrownBy(() -> NORMALIZER.normalize("not-a-sdk-body", context()))
                .isInstanceOf(ProviderCallException.class);
        assertThatThrownBy(() -> NORMALIZER.normalize(null, context()))
                .isInstanceOf(ProviderCallException.class);
    }

    @Test
    void confidenceExtraction() {
        assertThat(AliyunAuditResultNormalizer.extractConfidence("{\"confidence\":98.5}")).isEqualTo(98.5D);
        assertThat(AliyunAuditResultNormalizer.extractConfidence("{\"score\":\"0.66\"}")).isEqualTo(0.66D);
        assertThat(AliyunAuditResultNormalizer.extractConfidence("{\"riskLevel\":\"high\"}")).isNull();
        assertThat(AliyunAuditResultNormalizer.extractConfidence(null)).isNull();
        assertThat(AliyunAuditResultNormalizer.scaleOf(98.5D)).isEqualTo(ConfidenceScale.PERCENTAGE);
        assertThat(AliyunAuditResultNormalizer.scaleOf(0.66D)).isEqualTo(ConfidenceScale.ZERO_TO_ONE);
        assertThat(AliyunAuditResultNormalizer.scaleOf(null)).isEqualTo(ConfidenceScale.ZERO_TO_ONE);
    }

    private static Map<String, Object> context() {
        return AliyunAuditResultNormalizer.context(PLAN, "data-1", Duration.ofMillis(1));
    }
}
