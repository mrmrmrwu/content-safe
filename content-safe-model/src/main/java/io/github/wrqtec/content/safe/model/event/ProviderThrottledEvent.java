package io.github.wrqtec.content.safe.model.event;

import java.time.Instant;

/**
 * 供应商本地限流事件（限流器拒绝时发布）。
 * 与 {@link ProviderDegradedEvent} 区分：本事件表示本地主动限流，而非对端故障降级
 *
 * @author L.David
 */
public record ProviderThrottledEvent(String provider, String operation, String reason, Instant occurredAt)
        implements ContentSafeEvent {

    public ProviderThrottledEvent {
        occurredAt = occurredAt == null ? Instant.now() : occurredAt;
    }

    @Override
    public String eventType() {
        return "PROVIDER_THROTTLED";
    }
}
