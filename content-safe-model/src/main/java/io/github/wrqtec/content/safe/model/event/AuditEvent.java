package io.github.wrqtec.content.safe.model.event;

import java.time.Instant;

import io.github.wrqtec.content.safe.api.AuditResult;
import io.github.wrqtec.content.safe.api.util.Asserts;

/**
 * 审核完成事件。
 *
 * @author L.David
 */
public record AuditEvent(String scene, AuditResult result, Instant occurredAt) implements ContentSafeEvent {

    public AuditEvent {
        Asserts.notNull(result, "result");
        occurredAt = occurredAt == null ? Instant.now() : occurredAt;
    }

    @Override
    public String eventType() {
        return "AUDIT";
    }
}
