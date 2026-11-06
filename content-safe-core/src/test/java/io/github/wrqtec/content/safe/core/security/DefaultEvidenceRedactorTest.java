package io.github.wrqtec.content.safe.core.security;

import java.util.List;
import java.util.Map;

import io.github.wrqtec.content.safe.api.RiskHit;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DefaultEvidenceRedactorTest {

    private final DefaultEvidenceRedactor redactor = new DefaultEvidenceRedactor();

    @Test
    void longEvidenceMasked() {
        RiskHit hit = new RiskHit("porn", List.of("porn"), 0.9, null, null,
                "这是一段违规原文内容", null, Map.of());
        RiskHit redacted = redactor.redact(hit);
        assertTrue(redacted.evidence().contains("***"));
        assertFalse(redacted.evidence().contains("违规原文"));
    }

    @Test
    void shortEvidenceFullyMasked() {
        RiskHit hit = new RiskHit("porn", List.of(), null, null, null, "abc", null, Map.of());
        assertEquals("***", redactor.redact(hit).evidence());
    }

    @Test
    void nullEvidencePassesThrough() {
        RiskHit hit = new RiskHit("porn", List.of(), null, null, null, null, null, Map.of());
        assertNull(redactor.redact(hit).evidence());
    }
}
