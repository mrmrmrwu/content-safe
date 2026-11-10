package io.github.wrqtec.content.safe.api;

import java.time.Duration;
import java.util.List;

import io.github.wrqtec.content.safe.api.enums.AuditSource;
import io.github.wrqtec.content.safe.api.enums.RiskSuggestion;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link AuditResult} 单元测试
 *
 * @author L.David
 */
class AuditResultTest {

    @Test
    void builderDefaultsArePassAndFinal() {
        AuditResult result = AuditResult.builder().provider("aliyun").build();
        assertTrue(result.ok());
        assertEquals(RiskSuggestion.PASS, result.suggestion());
        assertTrue(result.finalResult());
        assertEquals(AuditSource.SYNC, result.source());
        assertTrue(result.pass());
    }

    @Test
    void errorSuggestionIsNotPass() {
        AuditResult result = AuditResult.builder()
                .ok(false).suggestion(RiskSuggestion.ERROR).build();
        assertFalse(result.pass());
    }

    @Test
    void reviewSuggestionIsNotPass() {
        AuditResult result = AuditResult.builder().suggestion(RiskSuggestion.REVIEW).build();
        assertFalse(result.pass());
    }

    @Test
    void hitsAreImmutable() {
        AuditResult result = AuditResult.builder()
                .suggestion(RiskSuggestion.REJECT)
                .hits(List.of(new RiskHit("porn", List.of("porn"), 0.99, null, null, null, null, null)))
                .cost(Duration.ofMillis(120))
                .build();
        assertEquals(1, result.hits().size());
        assertThrows(UnsupportedOperationException.class,
                () -> result.hits().add(new RiskHit(null, null, null, null, null, null, null, null)));
        assertEquals(Duration.ofMillis(120), result.cost());
    }

    @Test
    void withersReplaceOnlyTargetField() {
        RiskHit hit = new RiskHit("porn", List.of("porn"), 0.99, null, null, null, null, null);
        AuditResult result = AuditResult.builder()
                .ok(true)
                .suggestion(RiskSuggestion.PASS)
                .source(AuditSource.SYNC)
                .provider("fake")
                .hits(List.of(hit))
                .build();

        AuditResult arbitrated = result.withSource(AuditSource.ARBITRATION);
        assertEquals(AuditSource.ARBITRATION, arbitrated.source());
        assertEquals(AuditSource.SYNC, result.source());

        AuditResult reviewed = result.withSuggestion(RiskSuggestion.REJECT);
        assertEquals(RiskSuggestion.REJECT, reviewed.suggestion());
        assertFalse(reviewed.pass());
        assertEquals(result.hits(), reviewed.hits());
        assertEquals("fake", reviewed.provider());

        AuditResult redacted = result.withHits(List.of());
        assertEquals(List.of(), redacted.hits());
        assertEquals(result.suggestion(), redacted.suggestion());
    }
}
