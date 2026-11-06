package io.github.wrqtec.content.safe.model.event;

import java.time.Instant;

import io.github.wrqtec.content.safe.api.AuditResult;

/**
 * 人工复核（REVIEW）事件
 *
 * @author L.David
 */
public record ReviewEvent(String scene, String dataId, AuditResult result, Instant occurredAt)
        implements ContentSafeEvent {

    public ReviewEvent {
        occurredAt = occurredAt == null ? Instant.now() : occurredAt;
    }

    @Override
    public String eventType() {
        return "REVIEW";
    }
}
