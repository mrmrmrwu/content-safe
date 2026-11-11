package io.github.wrqtec.content.safe.api.util;

import java.util.ArrayList;
import java.util.List;

import io.github.wrqtec.content.safe.api.AuditResult;
import io.github.wrqtec.content.safe.api.RiskHit;
import io.github.wrqtec.content.safe.api.enums.RiskSuggestion;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RiskHitCapsTest {

    private static RiskHit hit(String label, Double confidence) {
        return new RiskHit(label, List.of(label), confidence, null, null, null, null, null);
    }

    private static AuditResult resultOf(List<RiskHit> hits) {
        return AuditResult.builder()
                .suggestion(RiskSuggestion.REJECT)
                .provider("fake")
                .hits(hits)
                .build();
    }

    @Test
    void returnsSameInstanceWhenUnderLimit() {
        AuditResult result = resultOf(List.of(hit("a", 0.9D), hit("b", 0.1D)));
        assertSame(result, RiskHitCaps.capVideoHits(result));
        assertSame(result, RiskHitCaps.capVideoHits(result, RiskHitCaps.DEFAULT_VIDEO_LIMIT));
    }

    @Test
    void nonPositiveLimitReturnsOriginal() {
        AuditResult result = resultOf(List.of(hit("a", 0.9D)));
        assertSame(result, RiskHitCaps.capVideoHits(result, 0));
        assertSame(result, RiskHitCaps.capVideoHits(result, -1));
    }

    @Test
    void keepsTopConfidenceAndAppendsFlags() {
        List<RiskHit> hits = new ArrayList<>();
        for (int i = 0; i <= RiskHitCaps.DEFAULT_VIDEO_LIMIT; i++) {
            hits.add(hit("hit-" + i, i * 0.01D));
        }
        AuditResult capped = RiskHitCaps.capVideoHits(resultOf(hits));

        assertEquals(RiskHitCaps.DEFAULT_VIDEO_LIMIT, capped.hits().size());
        assertEquals(Boolean.TRUE, capped.raw().get("truncated"));
        assertEquals(hits.size(), capped.raw().get("totalHits"));
        assertEquals(0.50D, capped.hits().get(0).confidence());
        assertEquals(0.01D, capped.hits().get(capped.hits().size() - 1).confidence());
        assertTrue(capped.hits().stream().noneMatch(h -> "hit-0".equals(h.labelPath())));
        assertEquals(RiskSuggestion.REJECT, capped.suggestion());
    }

    @Test
    void nullConfidenceRanksLast() {
        AuditResult capped = RiskHitCaps.capVideoHits(
                resultOf(List.of(hit("mid", 0.5D), hit("unknown", null), hit("top", 0.9D))), 2);

        assertEquals(2, capped.hits().size());
        assertEquals("top", capped.hits().get(0).labelPath());
        assertEquals("mid", capped.hits().get(1).labelPath());
        assertEquals(3, capped.raw().get("totalHits"));
    }

    @Test
    void tieKeepsOriginalOrder() {
        AuditResult capped = RiskHitCaps.capVideoHits(
                resultOf(List.of(hit("a", 0.8D), hit("b", 0.8D), hit("c", 0.8D))), 2);

        assertEquals(List.of("a", "b"), capped.hits().stream().map(RiskHit::labelPath).toList());
        assertFalse(capped.hits().stream().anyMatch(h -> "c".equals(h.labelPath())));
    }
}
