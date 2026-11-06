package io.github.wrqtec.content.safe.core.arbitration;

import java.util.List;

import io.github.wrqtec.content.safe.api.AuditResult;
import io.github.wrqtec.content.safe.api.enums.RiskSuggestion;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FirstNonPassArbitrationPolicyTest {

    private final FirstNonPassArbitrationPolicy policy = new FirstNonPassArbitrationPolicy();

    private AuditResult of(RiskSuggestion suggestion) {
        return AuditResult.builder().ok(true).suggestion(suggestion).build();
    }

    @Test
    void rejectWinsOverReviewAndPass() {
        AuditResult result = policy.arbitrate(
                List.of(of(RiskSuggestion.PASS), of(RiskSuggestion.REJECT), of(RiskSuggestion.REVIEW)));
        assertEquals(RiskSuggestion.REJECT, result.suggestion());
    }

    @Test
    void allPassReturnsPass() {
        AuditResult result = policy.arbitrate(List.of(of(RiskSuggestion.PASS), of(RiskSuggestion.PASS)));
        assertEquals(RiskSuggestion.PASS, result.suggestion());
    }

    @Test
    void emptyListRejected() {
        assertThrows(IllegalArgumentException.class, () -> policy.arbitrate(List.of()));
    }
}
