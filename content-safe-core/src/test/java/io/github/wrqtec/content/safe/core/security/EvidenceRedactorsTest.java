package io.github.wrqtec.content.safe.core.security;

import java.util.List;
import java.util.Map;

import io.github.wrqtec.content.safe.api.AuditResult;
import io.github.wrqtec.content.safe.api.RiskHit;
import io.github.wrqtec.content.safe.api.enums.RiskSuggestion;
import io.github.wrqtec.content.safe.spi.EvidenceRedactor;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

class EvidenceRedactorsTest {

    private EvidenceRedactor redactorFor(String provider) {
        return redactorFor(provider, "***");
    }

    private EvidenceRedactor redactorFor(String provider, String evidence) {
        return new EvidenceRedactor() {
            @Override
            public String provider() {
                return provider;
            }

            @Override
            public RiskHit redact(RiskHit hit) {
                return new RiskHit(hit.labelPath(), hit.labels(), hit.confidence(), hit.confidenceScale(),
                        hit.source(), evidence, hit.coordinates(), hit.raw());
            }
        };
    }

    private AuditResult resultWithEvidence(String evidence) {
        RiskHit hit = new RiskHit("porn", List.of("porn"), 0.99, null, null,
                evidence, null, Map.of());
        return AuditResult.builder().ok(true).hits(List.of(hit)).build();
    }

    @Test
    void exactProviderMatchWinsOverWildcard() {
        // 通配脱敏器注册在前（如更高 @Order）也必须被精确命中者压过：
        // 两个脱敏器输出不同的 evidence，使断言能区分究竟谁执行了脱敏
        AuditResult result = EvidenceRedactors.redactResult(
                List.of(redactorFor("*", "[wild]"), redactorFor("fake", "[exact]")),
                resultWithEvidence("abc"), "fake");

        assertEquals("[exact]", result.hits().get(0).evidence());
    }

    @Test
    void wildcardAppliesWhenNoExactMatch() {
        AuditResult result = EvidenceRedactors.redactResult(
                List.of(redactorFor("other", "[exact]"), redactorFor("*", "[wild]")),
                resultWithEvidence("abc"), "fake");

        assertEquals("[wild]", result.hits().get(0).evidence());
    }

    @Test
    void firstRegisteredWinsWithinSamePriority() {
        AuditResult result = EvidenceRedactors.redactResult(
                List.of(redactorFor("fake", "[first]"), redactorFor("fake", "[second]")),
                resultWithEvidence("abc"), "fake");

        assertEquals("[first]", result.hits().get(0).evidence());
    }

    @Test
    void noMatchingRedactorReturnsSameInstance() {
        AuditResult original = resultWithEvidence("abc");

        AuditResult result = EvidenceRedactors.redactResult(
                List.of(redactorFor("other")), original, "fake");

        // 无命中脱敏器时原样返回同一实例，避免无谓复制
        assertSame(original, result);
    }

    @Test
    void nullResultOrEmptyHitsOrEmptyRedactorsShortCircuit() {
        AuditResult noHits = AuditResult.builder().ok(true).build();
        AuditResult original = resultWithEvidence("abc");

        assertNull(EvidenceRedactors.redactResult(List.of(redactorFor("*")), null, "fake"));
        assertSame(noHits, EvidenceRedactors.redactResult(List.of(redactorFor("*")), noHits, "fake"));
        assertSame(original, EvidenceRedactors.redactResult(List.of(), original, "fake"));
        assertSame(original, EvidenceRedactors.redactResult(null, original, "fake"));
    }

    @Test
    void otherFieldsCarriedOverUnchanged() {
        AuditResult original = AuditResult.builder()
                .ok(true).suggestion(RiskSuggestion.REJECT)
                .dataId("d1").provider("fake").build();

        AuditResult redacted = EvidenceRedactors.redactResult(
                List.of(redactorFor("fake")), original, "fake");

        assertEquals(original.suggestion(), redacted.suggestion());
        assertEquals(original.dataId(), redacted.dataId());
        assertEquals(original.provider(), redacted.provider());
        assertEquals(original.ok(), redacted.ok());
    }
}
