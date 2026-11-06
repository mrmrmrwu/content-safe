package io.github.wrqtec.content.safe.model.event;

import java.time.Instant;
import java.util.List;

import io.github.wrqtec.content.safe.api.RiskHit;
import io.github.wrqtec.content.safe.api.util.Asserts;

/**
 * 命中风险标签事件。
 *
 * @author L.David
 */
public record RiskHitEvent(String scene, String dataId, List<RiskHit> hits, Instant occurredAt)
        implements ContentSafeEvent {

    public RiskHitEvent {
        hits = hits == null ? List.of() : List.copyOf(hits);
        occurredAt = occurredAt == null ? Instant.now() : occurredAt;
    }

    @Override
    public String eventType() {
        return "RISK_HIT";
    }
}
