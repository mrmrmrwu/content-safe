package io.github.wrqtec.content.safe.core.listener;

import java.util.ArrayList;
import java.util.List;

import io.github.wrqtec.content.safe.api.AuditResult;
import io.github.wrqtec.content.safe.api.enums.RiskSuggestion;
import io.github.wrqtec.content.safe.model.event.AuditEvent;
import io.github.wrqtec.content.safe.model.event.ContentSafeEvent;
import io.github.wrqtec.content.safe.spi.AuditListener;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CompositeAuditListenerTest {

    @Test
    void allListenersInvokedEvenWhenOneThrows() {
        List<ContentSafeEvent> received = new ArrayList<>();
        AuditListener bad = event -> {
            throw new RuntimeException("boom");
        };
        AuditListener good = received::add;

        CompositeAuditListener composite = new CompositeAuditListener(List.of(bad, good));
        AuditEvent event = new AuditEvent("s",
                AuditResult.builder().ok(true).suggestion(RiskSuggestion.PASS).build(), null);
        composite.onEvent(event);

        assertEquals(1, received.size());
        assertEquals("AUDIT", received.get(0).eventType());
    }

    @Test
    void nullDelegatesTolerated() {
        CompositeAuditListener composite = new CompositeAuditListener((List<AuditListener>) null);
        composite.onEvent(new AuditEvent("s", AuditResult.builder().ok(true).build(), null));
    }
}
