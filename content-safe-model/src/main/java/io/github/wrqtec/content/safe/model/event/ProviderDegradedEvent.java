package io.github.wrqtec.content.safe.model.event;

import java.time.Instant;

/**
 * 供应商降级/不可用事件（熔断触发时发布）
 *
 * @author L.David
 */
public record ProviderDegradedEvent(String provider, String operation, String scene, String reason, Instant occurredAt)
        implements ContentSafeEvent {

    public ProviderDegradedEvent {
        occurredAt = occurredAt == null ? Instant.now() : occurredAt;
    }

    @Override
    public String eventType() {
        return "PROVIDER_DEGRADED";
    }
}
