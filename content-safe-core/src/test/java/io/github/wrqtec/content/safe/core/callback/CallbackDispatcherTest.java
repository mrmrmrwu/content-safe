package io.github.wrqtec.content.safe.core.callback;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import io.github.wrqtec.content.safe.api.AuditResult;
import io.github.wrqtec.content.safe.api.CallbackRequest;
import io.github.wrqtec.content.safe.api.CallbackResult;
import io.github.wrqtec.content.safe.api.RiskHit;
import io.github.wrqtec.content.safe.api.enums.ContentType;
import io.github.wrqtec.content.safe.api.enums.RiskSuggestion;
import io.github.wrqtec.content.safe.api.exception.CallbackVerifyException;
import io.github.wrqtec.content.safe.model.AsyncTaskRef;
import io.github.wrqtec.content.safe.model.AuditCapabilities;
import io.github.wrqtec.content.safe.model.AuditTask;
import io.github.wrqtec.content.safe.model.ProviderPlan;
import io.github.wrqtec.content.safe.model.SceneDefinition;
import io.github.wrqtec.content.safe.model.enums.TaskStatus;
import io.github.wrqtec.content.safe.model.event.AuditEvent;
import io.github.wrqtec.content.safe.model.event.CallbackDeadLetterEvent;
import io.github.wrqtec.content.safe.model.event.ContentSafeEvent;
import io.github.wrqtec.content.safe.core.task.InMemoryAuditTaskStore;
import io.github.wrqtec.content.safe.spi.AuditListener;
import io.github.wrqtec.content.safe.spi.AuditOperation;
import io.github.wrqtec.content.safe.spi.AuditOperationProvider;
import io.github.wrqtec.content.safe.spi.CallbackVerifier;
import io.github.wrqtec.content.safe.spi.EvidenceRedactor;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
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
                // 证据携带原文，供脱敏用例校验（默认无脱敏器时落库/事件保留原文）
                RiskHit hit = new RiskHit("porn", List.of("porn"), 0.99, null, null,
                        "user-content-original", null, Map.of());
                return CallbackResult.accepted("provider-task-1",
                        AuditResult.builder().ok(true).suggestion(RiskSuggestion.PASS)
                                .dataId("d1").hits(List.of(hit)).build());
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
                store, listener, List.of(), "secret");
    }

    private CallbackDispatcher dispatcher(CallbackVerifier verifier, List<EvidenceRedactor> redactors) {
        return new CallbackDispatcher(List.of(verifier), List.of(providerWithCallbackOp()),
                store, listener, redactors, "secret");
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

    @Test
    void acceptedCallbackStoresAndPublishesRedactedResult() {
        // EvidenceRedactor 契约：落库与事件必须是脱敏副本
        EvidenceRedactor maskProvider = new EvidenceRedactor() {
            @Override
            public String provider() {
                return "fake";
            }

            @Override
            public RiskHit redact(RiskHit hit) {
                return new RiskHit(hit.labelPath(), hit.labels(), hit.confidence(), hit.confidenceScale(),
                        hit.source(), "***", hit.coordinates(), hit.raw());
            }
        };
        store.save(AuditTask.pending("t1", new AsyncTaskRef("provider-task-1", "d1"), "avatar", "fake"));
        CallbackDispatcher dispatcher = dispatcher(verifierReturning(true), List.of(maskProvider));

        CallbackResult result = dispatcher.dispatch(new CallbackRequest("fake", Map.of(), "{}"));

        assertTrue(result.accepted());
        // 返回值已脱敏
        assertEquals("***", result.result().hits().get(0).evidence());
        // 落库结果已脱敏
        assertEquals("***", store.find("t1").orElseThrow().result().hits().get(0).evidence());
        // 事件携带结果已脱敏
        AuditEvent event = (AuditEvent) events.stream()
                .filter(e -> e instanceof AuditEvent).findFirst().orElseThrow();
        assertEquals("***", event.result().hits().get(0).evidence());
    }

    @Test
    void missingTaskCallbackDeadLettersButAccepts() {
        // 任务被逐出/应用重启后回调到达：发死信保可观测，返回受理止住供应商重推
        CallbackDispatcher dispatcher = dispatcher(verifierReturning(true));

        CallbackResult result = dispatcher.dispatch(new CallbackRequest("fake", Map.of(), "{}"));

        assertTrue(result.accepted());
        assertEquals("provider-task-1", result.taskId());
        assertTrue(events.stream().anyMatch(e -> e instanceof CallbackDeadLetterEvent dle
                && dle.reason().contains("audit task not found")));
    }

    @Test
    void rateLimitedCallbackIsRejectedBeforeVerify() {
        // qps=1：突发容量只放行 1 个，第二个立刻被拒；限流在验签前生效且不发死信事件
        AtomicBoolean verified = new AtomicBoolean();
        CallbackVerifier countingVerifier = new CallbackVerifier() {
            @Override
            public String provider() {
                return "fake";
            }

            @Override
            public boolean verify(CallbackRequest request, String secret, Map<String, String> ext) {
                verified.set(true);
                return true;
            }
        };
        store.save(AuditTask.pending("t1", new AsyncTaskRef("provider-task-1", "d1"), "avatar", "fake"));
        CallbackDispatcher dispatcher = new CallbackDispatcher(List.of(countingVerifier),
                List.of(providerWithCallbackOp()), store, listener, List.of(), "secret", 1.0D);

        CallbackRequest request = new CallbackRequest("fake", Map.of(), "{}");
        assertTrue(dispatcher.dispatch(request).accepted());

        // 首次成功派发已调用过 verifier，清零后单独观察第二次派发
        verified.set(false);
        CallbackResult second = dispatcher.dispatch(request);

        assertFalse(second.accepted());
        assertTrue(second.message().contains("rate limited"));
        // 限流在验签前：verifier 未被调用
        assertFalse(verified.get());
        // 限流拒绝不发死信事件，仅首个成功回调产生 AUDIT 事件
        assertEquals(1, events.size());
        assertTrue(events.get(0) instanceof AuditEvent);
    }

    @Test
    void nonPositiveQpsDisablesRateLimiting() {
        CallbackDispatcher dispatcher = new CallbackDispatcher(List.of(verifierReturning(true)),
                List.of(providerWithCallbackOp()), store, listener, List.of(), "secret", 0D);
        CallbackRequest request = new CallbackRequest("fake", Map.of(), "{}");

        for (int i = 0; i < 5; i++) {
            assertTrue(dispatcher.dispatch(request).accepted());
        }
    }

    @Test
    void legacyConstructorUnlimitedRate() {
        // 1.0.0 六参构造：不限流，连续多次都应放行
        CallbackDispatcher dispatcher = dispatcher(verifierReturning(true));
        CallbackRequest request = new CallbackRequest("fake", Map.of(), "{}");

        for (int i = 0; i < 5; i++) {
            assertTrue(dispatcher.dispatch(request).accepted());
        }
    }
}
