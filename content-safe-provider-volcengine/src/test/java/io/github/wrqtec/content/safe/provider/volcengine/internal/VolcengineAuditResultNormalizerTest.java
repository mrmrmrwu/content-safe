package io.github.wrqtec.content.safe.provider.volcengine.internal;

import com.volcengine.model.response.ResponseMetadata;
import com.volcengine.model.response.TextRiskResponse;
import io.github.wrqtec.content.safe.api.enums.RiskSuggestion;
import io.github.wrqtec.content.safe.api.exception.ProviderCallException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class VolcengineAuditResultNormalizerTest {

    private final VolcengineAuditResultNormalizer normalizer = new VolcengineAuditResultNormalizer();

    @Test
    void decisionMapping() {
        assertThat(VolcengineAuditResultNormalizer.fromDecision("pass")).isEqualTo(RiskSuggestion.PASS);
        assertThat(VolcengineAuditResultNormalizer.fromDecision("PASS")).isEqualTo(RiskSuggestion.PASS);
        assertThat(VolcengineAuditResultNormalizer.fromDecision("suspect")).isEqualTo(RiskSuggestion.REVIEW);
        assertThat(VolcengineAuditResultNormalizer.fromDecision("reject")).isEqualTo(RiskSuggestion.REJECT);
    }

    @Test
    void unknownDecisionFallsBackToReview() {
        assertThat(VolcengineAuditResultNormalizer.fromDecision("block")).isEqualTo(RiskSuggestion.REVIEW);
        assertThat(VolcengineAuditResultNormalizer.fromDecision(null)).isEqualTo(RiskSuggestion.REVIEW);
        assertThat(VolcengineAuditResultNormalizer.fromDecision("")).isEqualTo(RiskSuggestion.REVIEW);
    }

    @Test
    void unsupportedRawTypeThrows() {
        assertThatThrownBy(() -> normalizer.normalize("raw", null))
                .isInstanceOf(ProviderCallException.class)
                .hasMessageContaining("unsupported volcengine raw response type");
    }

    @Test
    void emptyResultThrows() {
        TextRiskResponse response = new TextRiskResponse();
        assertThatThrownBy(() -> normalizer.normalize(response, null))
                .isInstanceOf(ProviderCallException.class)
                .hasMessageContaining("empty result");
    }

    @Test
    void metadataErrorMapsToCallFailure() {
        TextRiskResponse response = new TextRiskResponse();
        ResponseMetadata metadata = new ResponseMetadata();
        ResponseMetadata.Error error = new ResponseMetadata.Error();
        error.setCode("InvalidCredential");
        error.setMessage("signature not match (valid for 5 minutes)");
        metadata.setRequestId("req-err-1");
        metadata.setError(error);
        response.setResponseMetadata(metadata);

        assertThatThrownBy(() -> normalizer.normalize(response, null))
                .isInstanceOf(ProviderCallException.class)
                .hasMessageContaining("InvalidCredential")
                .hasMessageContaining("req-err-1");
    }

    @Test
    void providerIsVolcengine() {
        assertThat(normalizer.provider()).isEqualTo("volcengine");
    }
}
