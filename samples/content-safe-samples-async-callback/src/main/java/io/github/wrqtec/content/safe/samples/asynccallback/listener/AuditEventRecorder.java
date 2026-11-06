package io.github.wrqtec.content.safe.samples.asynccallback.listener;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import io.github.wrqtec.content.safe.model.event.AuditEvent;
import io.github.wrqtec.content.safe.model.event.CallbackDeadLetterEvent;
import io.github.wrqtec.content.safe.model.event.ContentSafeEvent;
import io.github.wrqtec.content.safe.spi.AuditListener;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 事件收集器：观测回调受理后的 {@link AuditEvent} 与验签失败的 {@link CallbackDeadLetterEvent}。
 *
 * @author L.David
 */
@Slf4j
@Component
public class AuditEventRecorder implements AuditListener {

    private final List<ContentSafeEvent> events = new CopyOnWriteArrayList<>();

    @Override
    public void onEvent(ContentSafeEvent event) {
        events.add(event);
        if (event instanceof CallbackDeadLetterEvent dead) {
            log.info("event: {} provider={} reason={}", dead.eventType(), dead.provider(), dead.reason());
        } else {
            log.info("event: {}", event.eventType());
        }
    }

    public List<ContentSafeEvent> events() {
        return List.copyOf(events);
    }

    public List<AuditEvent> auditEvents() {
        return events.stream()
                .filter(AuditEvent.class::isInstance)
                .map(AuditEvent.class::cast)
                .toList();
    }

    public List<CallbackDeadLetterEvent> deadLetterEvents() {
        return events.stream()
                .filter(CallbackDeadLetterEvent.class::isInstance)
                .map(CallbackDeadLetterEvent.class::cast)
                .toList();
    }
}
