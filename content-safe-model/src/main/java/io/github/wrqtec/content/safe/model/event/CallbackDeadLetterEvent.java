package io.github.wrqtec.content.safe.model.event;

import java.time.Instant;

/**
 * 回调死信事件：验签失败 / 无法解析 / 任务不存在，保留轨迹且禁止静默丢弃
 * 不携带回调原文（审计红线）
 *
 * @author L.David
 */
public record CallbackDeadLetterEvent(String provider, String reason, Instant occurredAt)
        implements ContentSafeEvent {

    public CallbackDeadLetterEvent {
        occurredAt = occurredAt == null ? Instant.now() : occurredAt;
    }

    @Override
    public String eventType() {
        return "CALLBACK_DEAD_LETTER";
    }
}
