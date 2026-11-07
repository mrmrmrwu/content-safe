package io.github.wrqtec.content.safe.core;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import io.github.wrqtec.content.safe.api.AuditRequest;
import io.github.wrqtec.content.safe.api.AuditResult;
import io.github.wrqtec.content.safe.api.CallbackRequest;
import io.github.wrqtec.content.safe.api.ContentRef;
import io.github.wrqtec.content.safe.api.RiskHit;
import io.github.wrqtec.content.safe.api.constant.Scenes;
import io.github.wrqtec.content.safe.api.enums.ContentType;
import io.github.wrqtec.content.safe.api.enums.RiskSuggestion;
import io.github.wrqtec.content.safe.api.exception.AllProvidersFailedException;
import io.github.wrqtec.content.safe.api.exception.InvalidRequestException;
import io.github.wrqtec.content.safe.api.exception.ProviderCallException;
import io.github.wrqtec.content.safe.api.exception.ProviderNotAvailableException;
import io.github.wrqtec.content.safe.api.exception.SceneNotConfiguredException;
import io.github.wrqtec.content.safe.model.AuditCapabilities;
import io.github.wrqtec.content.safe.model.OperationLimits;
import io.github.wrqtec.content.safe.model.ProviderPlan;
import io.github.wrqtec.content.safe.model.SceneDefinition;
import io.github.wrqtec.content.safe.model.enums.AuditMode;
import io.github.wrqtec.content.safe.model.event.ContentSafeEvent;
import io.github.wrqtec.content.safe.core.callback.CallbackDispatcher;
import io.github.wrqtec.content.safe.core.degrade.CircuitBreaker;
import io.github.wrqtec.content.safe.core.degrade.CircuitBreakerRegistry;
import io.github.wrqtec.content.safe.core.degrade.SlidingWindowCircuitBreaker;
import io.github.wrqtec.content.safe.core.limiter.RateLimiter;
import io.github.wrqtec.content.safe.core.limiter.RateLimiterRegistry;
import io.github.wrqtec.content.safe.core.listener.CompositeAuditListener;
import io.github.wrqtec.content.safe.core.router.PriorityAuditRouter;
import io.github.wrqtec.content.safe.core.security.DefaultEvidenceRedactor;
import io.github.wrqtec.content.safe.core.task.InMemoryAuditTaskStore;
import io.github.wrqtec.content.safe.spi.AuditListener;
import io.github.wrqtec.content.safe.spi.AuditOperation;
import io.github.wrqtec.content.safe.spi.AuditOperationProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DefaultContentSafeTemplateTest {

    /** 可编程的假操作 */
    static class StubOperation implements AuditOperation {

        private final String provider;
        private final int order;
        private final OperationLimits limits;
        private RuntimeException failure;
        private int calls;

        StubOperation(String provider, int order) {
            this(provider, order, null);
        }

        StubOperation(String provider, int order, OperationLimits limits) {
            this.provider = provider;
            this.order = order;
            this.limits = limits;
        }

        void failWith(RuntimeException e) {
            this.failure = e;
        }

        int calls() {
            return calls;
        }

        @Override
        public ProviderPlan plan() {
            return new ProviderPlan(provider, "p", "1", "textScan", null, null, limits, null);
        }

        @Override
        public AuditCapabilities capabilities() {
            return AuditCapabilities.builder().contentTypes(ContentType.TEXT).sync(true).build();
        }

        @Override
        public int order() {
            return order;
        }

        @Override
        public AuditResult audit(AuditRequest request) {
            calls++;
            if (failure != null) {
                throw failure;
            }
            return AuditResult.builder()
                    .ok(true)
                    .suggestion(RiskSuggestion.PASS)
                    .dataId(request.dataId())
                    .provider(provider)
                    .build();
        }
    }

    static AuditOperationProvider providerOf(AuditOperation... ops) {
        return new AuditOperationProvider() {
            @Override
            public String provider() {
                return ops[0].plan().provider();
            }

            @Override
            public List<ContentType> supportedTypes() {
                return List.of(ContentType.TEXT);
            }

            @Override
            public List<AuditOperation> operations(SceneDefinition scene) {
                return List.of(ops);
            }
        };
    }

    private List<ContentSafeEvent> events;
    private AuditListener recording;
    private SceneRegistry scenes;

    @BeforeEach
    void setUp() {
        events = new ArrayList<>();
        recording = events::add;
        scenes = new SceneRegistry(List.of());
    }

    private DefaultContentSafeTemplate templateOf(AuditOperationProvider... providers) {
        return templateOf(new RateLimiterRegistry(), providers);
    }

    private DefaultContentSafeTemplate templateOf(RateLimiterRegistry limiters,
                                                  AuditOperationProvider... providers) {
        return new DefaultContentSafeTemplate(scenes, List.of(providers), new PriorityAuditRouter(),
                new CircuitBreakerRegistry(), limiters, new CompositeAuditListener(List.of(recording)),
                List.of(new DefaultEvidenceRedactor()), new InMemoryAuditTaskStore(), null);
    }

    @Test
    void auditHappyPathPublishesEvents() {
        DefaultContentSafeTemplate template = templateOf(providerOf(new StubOperation("fake", 0)));
        AuditResult result = template.text(Scenes.NICKNAME, "你好");

        assertTrue(result.pass());
        assertEquals("fake", result.provider());
        assertTrue(events.stream().anyMatch(e -> "AUDIT".equals(e.eventType())));
    }

    @Test
    void unknownSceneThrowsSceneNotConfigured() {
        DefaultContentSafeTemplate template = templateOf(providerOf(new StubOperation("fake", 0)));
        assertThrows(SceneNotConfiguredException.class, () -> template.text("unknown_scene", "t"));
    }

    @Test
    void sceneTypeMismatchRejected() {
        DefaultContentSafeTemplate template = templateOf(providerOf(new StubOperation("fake", 0)));
        AuditRequest image = AuditRequest.image(Scenes.NICKNAME, ContentRef.url("https://x/a.jpg"));
        assertThrows(InvalidRequestException.class, () -> template.audit(image));
    }

    @Test
    void noProviderConfiguredThrowsNotAvailable() {
        DefaultContentSafeTemplate template = templateOf();
        assertThrows(ProviderNotAvailableException.class, () -> template.text(Scenes.NICKNAME, "t"));
    }

    @Test
    void asyncOperationsExcludedFromSyncRouting() {
        StubOperation sync = new StubOperation("sync-p", 0);
        StubOperation async = new StubOperation("async-p", 1) {
            @Override
            public ProviderPlan plan() {
                return new ProviderPlan("async-p", "p", "1", "imageAsync", null,
                        AuditMode.ASYNC_QUERY, null, null);
            }

            @Override
            public AuditResult audit(AuditRequest request) {
                throw new IllegalStateException("async op must not be routed by audit()");
            }
        };
        DefaultContentSafeTemplate template = templateOf(providerOf(sync, async));

        AuditResult result = template.text(Scenes.NICKNAME, "你好");
        assertEquals("sync-p", result.provider());
        assertEquals(1, sync.calls());
        assertEquals(0, async.calls());
    }

    @Test
    void failoverToNextProviderOnFailure() {
        StubOperation bad = new StubOperation("bad", 0);
        bad.failWith(new ProviderCallException("timeout"));
        StubOperation good = new StubOperation("good", 1);
        DefaultContentSafeTemplate template = templateOf(providerOf(bad), providerOf(good));

        AuditResult result = template.text(Scenes.NICKNAME, "你好");
        assertEquals("good", result.provider());
        assertTrue(events.stream().anyMatch(e -> "PROVIDER_DEGRADED".equals(e.eventType())));
    }

    @Test
    void allProvidersFailedThrows() {
        StubOperation a = new StubOperation("a", 0);
        a.failWith(new ProviderCallException("boom"));
        StubOperation b = new StubOperation("b", 1);
        b.failWith(new ProviderCallException("boom"));
        DefaultContentSafeTemplate template = templateOf(providerOf(a), providerOf(b));

        assertThrows(AllProvidersFailedException.class, () -> template.text(Scenes.NICKNAME, "t"));
    }

    @Test
    void riskResultIsRedactedAndPublishesRiskHitEvent() {
        StubOperation op = new StubOperation("fake", 0) {
            @Override
            public AuditResult audit(AuditRequest request) {
                RiskHit hit = new RiskHit("porn", List.of("porn"), 0.99, null, null,
                        "这是一段违规原文内容", null, Map.of());
                return AuditResult.builder().ok(true).suggestion(RiskSuggestion.REJECT)
                        .hits(List.of(hit)).provider("fake").build();
            }
        };
        DefaultContentSafeTemplate template = templateOf(providerOf(op));

        AuditResult result = template.text(Scenes.NICKNAME, "t");
        assertEquals(RiskSuggestion.REJECT, result.suggestion());
        assertTrue(result.hits().get(0).evidence().contains("***"));
        assertTrue(events.stream().anyMatch(e -> "RISK_HIT".equals(e.eventType())));
    }

    @Test
    void callbackRequiresDispatcher() {
        DefaultContentSafeTemplate template = templateOf(providerOf(new StubOperation("fake", 0)));
        assertThrows(InvalidRequestException.class,
                () -> template.handleCallback(new CallbackRequest("fake", Map.of(), "{}")));
    }

    @Test
    void circuitOpensAfterFailures() {
        CircuitBreakerRegistry registry = new CircuitBreakerRegistry(
                () -> new SlidingWindowCircuitBreaker(4, 0.5D, 4, Duration.ofMinutes(10), 1));
        StubOperation bad = new StubOperation("bad", 0);
        bad.failWith(new ProviderCallException("boom"));
        DefaultContentSafeTemplate template = new DefaultContentSafeTemplate(scenes,
                List.of(providerOf(bad)), new PriorityAuditRouter(), registry,
                new RateLimiterRegistry(), new CompositeAuditListener(List.of()), List.of(),
                new InMemoryAuditTaskStore(), null);

        // 连续失败直至熔断
        for (int i = 0; i < 4; i++) {
            assertThrows(AllProvidersFailedException.class, () -> template.text(Scenes.NICKNAME, "t"));
        }
        // 熔断后请求直接失败（不再调用 provider）
        assertThrows(AllProvidersFailedException.class, () -> template.text(Scenes.NICKNAME, "t"));
        assertEquals(CircuitBreaker.State.OPEN, registry.get("bad:textScan").state());
    }

    /** 始终拒绝的限流器（模拟额度耗尽） */
    private static RateLimiter rejectingLimiter() {
        return new RateLimiter() {
            @Override
            public Permit tryAcquire() {
                return Permit.rejected();
            }

            @Override
            public String name() {
                return "reject-all";
            }
        };
    }

    @Test
    void rateLimitedFailsOverToNextProvider() {
        StubOperation throttled = new StubOperation("bad", 0);
        StubOperation good = new StubOperation("good", 1);
        RateLimiterRegistry limiters = new RateLimiterRegistry(
                (planKey, limits) -> planKey.startsWith("bad") ? rejectingLimiter() : RateLimiter.unlimited());
        DefaultContentSafeTemplate template = templateOf(limiters, providerOf(throttled), providerOf(good));

        AuditResult result = template.text(Scenes.NICKNAME, "你好");
        assertEquals("good", result.provider());
        assertEquals(0, throttled.calls());
        assertTrue(events.stream().anyMatch(e -> "PROVIDER_THROTTLED".equals(e.eventType())));
    }

    @Test
    void allProvidersRateLimitedThrows() {
        StubOperation a = new StubOperation("a", 0);
        StubOperation b = new StubOperation("b", 1);
        RateLimiterRegistry limiters = new RateLimiterRegistry((planKey, limits) -> rejectingLimiter());
        DefaultContentSafeTemplate template = templateOf(limiters, providerOf(a), providerOf(b));

        AllProvidersFailedException e = assertThrows(AllProvidersFailedException.class,
                () -> template.text(Scenes.NICKNAME, "t"));
        assertTrue(e.getMessage().contains("rate limited"));
        assertEquals(2, events.stream().filter(ev -> "PROVIDER_THROTTLED".equals(ev.eventType())).count());
    }

    @Test
    void concurrencyPermitReleasedOnFailure() {
        // 并发上限 1：若异常路径许可泄漏，第二次调用会被限流而非再次打到 provider
        OperationLimits limits = new OperationLimits(1, null, null, 0L, 0L, false);
        StubOperation bad = new StubOperation("bad", 0, limits);
        bad.failWith(new ProviderCallException("boom"));
        DefaultContentSafeTemplate template = templateOf(providerOf(bad));

        assertThrows(AllProvidersFailedException.class, () -> template.text(Scenes.NICKNAME, "t"));
        assertThrows(AllProvidersFailedException.class, () -> template.text(Scenes.NICKNAME, "t"));
        assertEquals(2, bad.calls());
    }
}
