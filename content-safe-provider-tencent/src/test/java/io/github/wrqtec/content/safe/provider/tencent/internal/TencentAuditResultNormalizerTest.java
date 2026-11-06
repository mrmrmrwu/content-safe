package io.github.wrqtec.content.safe.provider.tencent.internal;

import java.time.Duration;
import java.util.Map;

import com.tencentcloudapi.ims.v20201229.models.ImageModerationResponse;
import com.tencentcloudapi.tms.v20201229.models.TextModerationResponse;
import io.github.wrqtec.content.safe.api.AuditResult;
import io.github.wrqtec.content.safe.api.enums.RiskSuggestion;
import io.github.wrqtec.content.safe.api.exception.ProviderCallException;
import io.github.wrqtec.content.safe.model.OperationLimits;
import io.github.wrqtec.content.safe.model.ProviderPlan;
import io.github.wrqtec.content.safe.provider.tencent.TencentAuditOperationProvider;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link TencentAuditResultNormalizer} 单元测试（边界与回退规则）
 *
 * @author L.David
 */
class TencentAuditResultNormalizerTest {

    private final TencentAuditResultNormalizer normalizer = new TencentAuditResultNormalizer();

    private final ProviderPlan plan = TencentAuditOperationProvider.syncPlan(
            "TextModeration", "comment", new OperationLimits(null, null, null, 0, 0, false));

    @Test
    void fromSuggestionMapping() {
        assertThat(TencentAuditResultNormalizer.fromSuggestion("Pass")).isEqualTo(RiskSuggestion.PASS);
        assertThat(TencentAuditResultNormalizer.fromSuggestion("Review")).isEqualTo(RiskSuggestion.REVIEW);
        assertThat(TencentAuditResultNormalizer.fromSuggestion("Block")).isEqualTo(RiskSuggestion.REJECT);
        assertThat(TencentAuditResultNormalizer.fromSuggestion(null)).isNull();
        assertThat(TencentAuditResultNormalizer.fromSuggestion("unknown")).isNull();
    }

    @Test
    void fallsBackToLabelWhenSuggestionMissing() {
        TextModerationResponse normal = new TextModerationResponse();
        normal.setRequestId("r1");
        normal.setLabel("Normal");
        assertThat(normalizer.normalize(normal, context()).suggestion()).isEqualTo(RiskSuggestion.PASS);

        TextModerationResponse risky = new TextModerationResponse();
        risky.setRequestId("r2");
        risky.setLabel("Porn");
        risky.setScore(88L);
        AuditResult result = normalizer.normalize(risky, context());
        assertThat(result.suggestion()).isEqualTo(RiskSuggestion.REJECT);
        assertThat(result.hits()).hasSize(1);
    }

    @Test
    void imageFallsBackToTopLabelWhenDetailsEmpty() {
        ImageModerationResponse response = new ImageModerationResponse();
        response.setRequestId("r3");
        response.setSuggestion("Block");
        response.setLabel("Illegal");
        response.setSubLabel("Politics");
        response.setScore(90L);
        AuditResult result = normalizer.normalize(response, context());
        assertThat(result.suggestion()).isEqualTo(RiskSuggestion.REJECT);
        assertThat(result.hits()).hasSize(1);
        assertThat(result.hits().get(0).labelPath()).isEqualTo("Illegal/Politics");
    }

    @Test
    void unknownRawTypeRejected() {
        assertThatThrownBy(() -> normalizer.normalize(new Object(), context()))
                .isInstanceOf(ProviderCallException.class)
                .hasMessageContaining("unsupported tencent raw response type");
        assertThatThrownBy(() -> normalizer.normalize(null, context()))
                .isInstanceOf(ProviderCallException.class);
    }

    @Test
    void contextCarriesServiceDataIdCost() {
        Map<String, Object> context = TencentAuditResultNormalizer.context(plan, "d-9", Duration.ofMillis(7));
        assertThat(context)
                .containsEntry(TencentAuditResultNormalizer.CONTEXT_SERVICE, "comment")
                .containsEntry(TencentAuditResultNormalizer.CONTEXT_DATA_ID, "d-9")
                .containsEntry(TencentAuditResultNormalizer.CONTEXT_COST, Duration.ofMillis(7));
    }

    private Map<String, Object> context() {
        return TencentAuditResultNormalizer.context(plan, null, Duration.ofMillis(5));
    }
}
