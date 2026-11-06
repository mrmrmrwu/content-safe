package io.github.wrqtec.content.safe.samples.routingfailover.listener;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import io.github.wrqtec.content.safe.model.event.ContentSafeEvent;
import io.github.wrqtec.content.safe.model.event.ProviderDegradedEvent;
import io.github.wrqtec.content.safe.spi.AuditListener;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 事件收集器：记录全部治理事件，failover 发生时可见 {@link ProviderDegradedEvent}。
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
        if (event instanceof ProviderDegradedEvent degraded) {
            log.info("event: {} provider={} operation={} reason={}",
                    degraded.eventType(), degraded.provider(), degraded.operation(), degraded.reason());
        } else {
            log.info("event: {}", event.eventType());
        }
    }

    public List<ContentSafeEvent> events() {
        return List.copyOf(events);
    }

    public List<ProviderDegradedEvent> degradedEvents() {
        return events.stream()
                .filter(ProviderDegradedEvent.class::isInstance)
                .map(ProviderDegradedEvent.class::cast)
                .toList();
    }
}
