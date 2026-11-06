package io.github.wrqtec.content.safe.core.callback;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import io.github.wrqtec.content.safe.api.AuditResult;
import io.github.wrqtec.content.safe.api.CallbackRequest;
import io.github.wrqtec.content.safe.api.CallbackResult;
import io.github.wrqtec.content.safe.api.enums.ContentType;
import io.github.wrqtec.content.safe.api.enums.RiskSuggestion;
import io.github.wrqtec.content.safe.api.exception.CallbackVerifyException;
import io.github.wrqtec.content.safe.model.AsyncTaskRef;
import io.github.wrqtec.content.safe.model.AuditCapabilities;
import io.github.wrqtec.content.safe.model.AuditTask;
import io.github.wrqtec.content.safe.model.ProviderPlan;
import io.github.wrqtec.content.safe.model.SceneDefinition;
import io.github.wrqtec.content.safe.model.enums.TaskStatus;
import io.github.wrqtec.content.safe.model.event.ContentSafeEvent;
import io.github.wrqtec.content.safe.core.task.InMemoryAuditTaskStore;
import io.github.wrqtec.content.safe.spi.AuditListener;
import io.github.wrqtec.content.safe.spi.AuditOperation;
import io.github.wrqtec.content.safe.spi.AuditOperationProvider;
import io.github.wrqtec.content.safe.spi.CallbackVerifier;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CallbackDispatcherTest {

    private final List<ContentSafeEvent> events = new ArrayList<>();
    private final AuditListener listener = events::add;
    private final InMemoryAuditTaskStore store = new InMemoryAuditTaskStore();

    private CallbackVerifier verifierReturning(boolean ok) {
        return new CallbackVerifier() {
            @Override
            public String provider() {
                return "fake";
            }

            @Override
            public boolean verify(CallbackRequest request, String secret, Map<String, String> ext) {
                return ok;
            }
        };
    }

    private AuditOperationProvider providerWithCallbackOp() {
        AuditOperation op = new AuditOperation() {
            @Override
            public ProviderPlan plan() {
                return new ProviderPlan("fake", "p", "1", "imgAsync", null, null, null, null);
            }

            @Override
            public AuditCapabilities capabilities() {
                return AuditCapabilities.builder()
                        .contentTypes(ContentType.IMAGE).asyncCallback(true).build();
            }

            @Override
            public AuditResult audit(io.github.wrqtec.content.safe.api.AuditRequest request) {
                throw new UnsupportedOperationException();
            }

            @Override
            public CallbackResult handleCallback(CallbackRequest request) {
                return CallbackResult.accepted("provider-task-1",
                        AuditResult.builder().ok(true).suggestion(RiskSuggestion.PASS).dataId("d1").build());
            }
        };
        return new AuditOperationProvider() {
            @Override
            public String provider() {
                return "fake";
            }

            @Override
            public List<ContentType> supportedTypes() {
                return List.of(ContentType.IMAGE);
            }

            @Override
            public List<AuditOperation> operations(SceneDefinition scene) {
                return List.of(op);
            }
        };
    }

    private CallbackDispatcher dispatcher(CallbackVerifier verifier) {
        return new CallbackDispatcher(List.of(verifier), List.of(providerWithCallbackOp()),
                store, listener, "secret");
    }

    @Test
    void verifyFailureThrowsAndDeadLetters() {
        CallbackDispatcher dispatcher = dispatcher(verifierReturning(false));
        CallbackRequest request = new CallbackRequest("fake", Map.of(), "{}");

        assertThrows(CallbackVerifyException.class, () -> dispatcher.dispatch(request));
        assertTrue(events.stream().anyMatch(e -> "CALLBACK_DEAD_LETTER".equals(e.eventType())));
    }

    @Test
    void acceptedCallbackMarksTaskTerminalAndPublishesEvent() {
        store.save(AuditTask.pending("t1", new AsyncTaskRef("provider-task-1", "d1"), "avatar", "fake"));
        CallbackDispatcher dispatcher = dispatcher(verifierReturning(true));

        CallbackResult result = dispatcher.dispatch(new CallbackRequest("fake", Map.of(), "{}"));

        assertTrue(result.accepted());
        assertEquals(TaskStatus.SUCCEEDED, store.find("t1").orElseThrow().status());
        assertTrue(events.stream().anyMatch(e -> "AUDIT".equals(e.eventType())));
    }

    @Test
    void duplicateCallbackIsIdempotent() {
        store.save(AuditTask.pending("t1", new AsyncTaskRef("provider-task-1", "d1"), "avatar", "fake"));
        CallbackDispatcher dispatcher = dispatcher(verifierReturning(true));

        dispatcher.dispatch(new CallbackRequest("fake", Map.of(), "{}"));
        dispatcher.dispatch(new CallbackRequest("fake", Map.of(), "{}"));

        assertEquals(TaskStatus.SUCCEEDED, store.find("t1").orElseThrow().status());
        // 第二次回调不应重复发布 AUDIT 事件
        assertEquals(1, events.stream().filter(e -> "AUDIT".equals(e.eventType())).count());
    }

    @Test
    void unknownProviderGoesDeadLetter() {
        CallbackDispatcher dispatcher = dispatcher(verifierReturning(true));
        CallbackResult result = dispatcher.dispatch(new CallbackRequest("unknown", Map.of(), "{}"));

        assertFalse(result.accepted());
        assertTrue(events.stream().anyMatch(e -> "CALLBACK_DEAD_LETTER".equals(e.eventType())));
    }
}
