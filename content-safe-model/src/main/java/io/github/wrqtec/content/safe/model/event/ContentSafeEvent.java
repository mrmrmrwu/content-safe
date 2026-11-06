package io.github.wrqtec.content.safe.model.event;

import java.time.Instant;

/**
 * 内容审核事件。core 发布，业务侧通过 {@code AuditListener} 消费。
 * sealed 体系，全部实现为不可变 record。
 *
 * @author L.David
 */
public sealed interface ContentSafeEvent
        permits AuditEvent, RiskHitEvent, ReviewEvent, ProviderDegradedEvent, CallbackDeadLetterEvent {

    /**
     * 事件类型标识（供序列化/路由）。
     *
     * @return 事件类型字符串，如 {@code AUDIT}
     */
    String eventType();

    /**
     * 发生时间。
     *
     * @return 事件发生时刻，不为 null
     */
    Instant occurredAt();
}
