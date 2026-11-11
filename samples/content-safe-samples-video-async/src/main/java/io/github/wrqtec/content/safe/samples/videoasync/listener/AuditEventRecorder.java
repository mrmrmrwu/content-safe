package io.github.wrqtec.content.safe.samples.videoasync.listener;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import io.github.wrqtec.content.safe.model.event.AuditEvent;
import io.github.wrqtec.content.safe.model.event.ContentSafeEvent;
import io.github.wrqtec.content.safe.spi.AuditListener;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 事件收集器：观测轮询到终态后发布的 {@link AuditEvent} / {@link io.github.wrqtec.content.safe.model.event.RiskHitEvent}。
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
        log.info("event: {} scene={}", event.eventType(),
                event instanceof AuditEvent audit ? audit.scene() : "-");
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
}
