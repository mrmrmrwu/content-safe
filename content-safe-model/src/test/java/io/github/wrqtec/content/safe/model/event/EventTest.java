package io.github.wrqtec.content.safe.model.event;

import java.time.Instant;
import java.util.List;

import io.github.wrqtec.content.safe.api.AuditResult;
import io.github.wrqtec.content.safe.api.enums.RiskSuggestion;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 事件 record（{@link AuditEvent} 等）单元测试
 *
 * @author L.David
 */
class EventTest {

    @Test
    void eventTypeAndDefaults() {
        AuditEvent event = new AuditEvent("s",
                AuditResult.builder().dataId("d").suggestion(RiskSuggestion.PASS).build(), null);
        assertEquals("AUDIT", event.eventType());
        assertNotNull(event.occurredAt());

        CallbackDeadLetterEvent dead = new CallbackDeadLetterEvent("p", "verify failed", null);
        assertEquals("CALLBACK_DEAD_LETTER", dead.eventType());

        ProviderThrottledEvent throttled = new ProviderThrottledEvent("p", "op", "comment", "rate limited", null);
        assertEquals("PROVIDER_THROTTLED", throttled.eventType());
        assertNotNull(throttled.occurredAt());
    }

    @Test
    void riskHitListImmutable() {
        RiskHitEvent event = new RiskHitEvent("s", "d", List.of(), null);
        assertThrows(UnsupportedOperationException.class, () -> event.hits().add(null));
    }
}
