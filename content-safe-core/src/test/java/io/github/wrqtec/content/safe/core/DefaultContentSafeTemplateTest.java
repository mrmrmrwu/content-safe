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
import io.github.wrqtec.content.safe.api.enums.AuditSource;
import io.github.wrqtec.content.safe.api.enums.ContentType;
import io.github.wrqtec.content.safe.api.enums.RiskSuggestion;
import io.github.wrqtec.content.safe.api.exception.AllProvidersFailedException;
import io.github.wrqtec.content.safe.api.exception.InvalidRequestException;
import io.github.wrqtec.content.safe.api.exception.ProviderCallException;
import io.github.wrqtec.content.safe.api.exception.ProviderNotAvailableException;
import io.github.wrqtec.content.safe.api.exception.SceneNotConfiguredException;
import io.github.wrqtec.content.safe.api.exception.UnsupportedFeatureException;
import io.github.wrqtec.content.safe.model.AuditTask;
import io.github.wrqtec.content.safe.model.AsyncTaskRef;
import io.github.wrqtec.content.safe.model.AuditCapabilities;
import io.github.wrqtec.content.safe.model.OperationLimits;
import io.github.wrqtec.content.safe.model.ProviderPlan;
import io.github.wrqtec.content.safe.model.SceneDefinition;
import io.github.wrqtec.content.safe.model.enums.AuditMode;
import io.github.wrqtec.content.safe.model.enums.TaskStatus;
import io.github.wrqtec.content.safe.model.event.ContentSafeEvent;
import io.github.wrqtec.content.safe.core.arbitration.FirstNonPassArbitrationPolicy;
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
import io.github.wrqtec.content.safe.spi.AuditArbitrationSelector;
import io.github.wrqtec.content.safe.spi.AuditListener;
import io.github.wrqtec.content.safe.spi.AuditOperation;
import io.github.wrqtec.content.safe.spi.AuditOperationProvider;
import io.github.wrqtec.content.safe.spi.EvidenceRedactor;
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

    private DefaultContentSafeTemplate templateOf(AuditArbitrationSelector selector,
                                                  AuditOperationProvider... providers) {
        return new DefaultContentSafeTemplate(scenes, List.of(providers), new PriorityAuditRouter(),
                new CircuitBreakerRegistry(), new RateLimiterRegistry(),
                new CompositeAuditListener(List.of(recording)),
                List.of(new DefaultEvidenceRedactor()), new InMemoryAuditTaskStore(), null,
                null, selector, null, null);
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

    @Test
    void arbitrationFansOutAllProvidersAndMergesResults() {
        StubOperation pass = new StubOperation("pass-p", 0);
        int[] rejectCalls = {0};
        StubOperation reject = new StubOperation("reject-p", 1) {
            @Override
            public AuditResult audit(AuditRequest request) {
                rejectCalls[0]++;
                return AuditResult.builder()
                        .ok(true)
                        .suggestion(RiskSuggestion.REJECT)
                        .provider("reject-p")
                        .build();
            }
        };
        DefaultContentSafeTemplate template = templateOf(scene -> new FirstNonPassArbitrationPolicy(),
                providerOf(pass), providerOf(reject));

        AuditResult result = template.text(Scenes.NICKNAME, "t");

        // 主备 failover 会首个成功即返回，仲裁模式必须全量采集
        assertEquals(1, pass.calls());
        assertEquals(1, rejectCalls[0]);
        assertEquals(RiskSuggestion.REJECT, result.suggestion());
        assertEquals(AuditSource.ARBITRATION, result.source());
        assertEquals("reject-p", result.provider());
    }

    @Test
    void arbitrationFailureOfOneProviderStillReturnsResult() {
        StubOperation bad = new StubOperation("bad-p", 0);
        bad.failWith(new ProviderCallException("boom"));
        StubOperation good = new StubOperation("good-p", 1);
        DefaultContentSafeTemplate template = templateOf(scene -> new FirstNonPassArbitrationPolicy(),
                providerOf(bad), providerOf(good));

        AuditResult result = template.text(Scenes.NICKNAME, "t");

        assertEquals(RiskSuggestion.PASS, result.suggestion());
        assertEquals("good-p", result.provider());
        assertTrue(events.stream().anyMatch(e -> "AUDIT".equals(e.eventType())));
    }

    @Test
    void nullArbitrationSelectorKeepsFailoverSemantics() {
        StubOperation pass = new StubOperation("pass-p", 0);
        StubOperation never = new StubOperation("never-p", 1);
        DefaultContentSafeTemplate template = templateOf((AuditArbitrationSelector) null,
                providerOf(pass), providerOf(never));

        template.text(Scenes.NICKNAME, "t");

        // selector 为 null 回退主备 failover：首个成功后不再调用后续供应商
        assertEquals(1, pass.calls());
        assertEquals(0, never.calls());
    }

    @Test
    void submitAndWaitPollsUntilFinalResult() {
        StubOperation async = new StubOperation("fake", 0) {
            private int queries;

            @Override
            public AuditCapabilities capabilities() {
                return AuditCapabilities.builder().contentTypes(ContentType.TEXT)
                        .sync(true).asyncQuery(true).build();
            }

            @Override
            public AsyncTaskRef submitAsync(AuditRequest request) {
                return new AsyncTaskRef("task-1", "d1");
            }

            @Override
            public AuditResult query(AsyncTaskRef ref) {
                queries++;
                if (queries < 2) {
                    return AuditResult.builder().ok(true).finalResult(false).build();
                }
                return AuditResult.builder().ok(true).suggestion(RiskSuggestion.REJECT).build();
            }
        };
        DefaultContentSafeTemplate template = templateOf(providerOf(async));

        AuditResult result = template.submitAndWait(AuditRequest.text(Scenes.NICKNAME, "t"),
                Duration.ofMillis(10), Duration.ofSeconds(5));

        assertEquals(RiskSuggestion.REJECT, result.suggestion());
        assertTrue(result.finalResult());
    }

    @Test
    void submitAndWaitTimesOutWhenTaskNeverFinishes() {
        StubOperation async = new StubOperation("fake", 0) {
            @Override
            public AuditCapabilities capabilities() {
                return AuditCapabilities.builder().contentTypes(ContentType.TEXT)
                        .sync(true).asyncQuery(true).build();
            }

            @Override
            public AsyncTaskRef submitAsync(AuditRequest request) {
                return new AsyncTaskRef("task-1", "d1");
            }

            @Override
            public AuditResult query(AsyncTaskRef ref) {
                return AuditResult.builder().ok(true).finalResult(false).build();
            }
        };
        DefaultContentSafeTemplate template = templateOf(providerOf(async));

        ProviderCallException e = assertThrows(ProviderCallException.class,
                () -> template.submitAndWait(AuditRequest.text(Scenes.NICKNAME, "t"),
                        Duration.ofMillis(10), Duration.ofMillis(60)));
        assertTrue(e.getMessage().contains("timed out"));
    }

    @Test
    void submitAndWaitRejectsInvalidArguments() {
        DefaultContentSafeTemplate template = templateOf(providerOf(new StubOperation("fake", 0)));

        assertThrows(InvalidRequestException.class, () -> template.submitAndWait(null));
        assertThrows(InvalidRequestException.class,
                () -> template.submitAndWait(AuditRequest.text(Scenes.NICKNAME, "t"), Duration.ZERO, Duration.ofSeconds(1)));
        assertThrows(InvalidRequestException.class,
                () -> template.submitAndWait(AuditRequest.text(Scenes.NICKNAME, "t"), Duration.ofSeconds(1), Duration.ofSeconds(-1)));
    }

    @Test
    void queryStoresAndReturnsRedactedResult() {
        EvidenceRedactor mask = new EvidenceRedactor() {
            @Override
            public String provider() {
                return "*";
            }

            @Override
            public RiskHit redact(RiskHit hit) {
                return new RiskHit(hit.labelPath(), hit.labels(), hit.confidence(), hit.confidenceScale(),
                        hit.source(), "***", hit.coordinates(), hit.raw());
            }
        };
        InMemoryAuditTaskStore store = new InMemoryAuditTaskStore();
        StubOperation async = new StubOperation("fake", 0) {
            @Override
            public AuditCapabilities capabilities() {
                return AuditCapabilities.builder().contentTypes(ContentType.TEXT)
                        .sync(true).asyncQuery(true).build();
            }

            @Override
            public AuditResult query(AsyncTaskRef ref) {
                RiskHit hit = new RiskHit("porn", List.of("porn"), 0.99, null, null,
                        "secret-evidence", null, Map.of());
                return AuditResult.builder().ok(true).suggestion(RiskSuggestion.REJECT)
                        .finalResult(true).hits(List.of(hit)).build();
            }
        };
        DefaultContentSafeTemplate template = new DefaultContentSafeTemplate(scenes,
                List.of(providerOf(async)), new PriorityAuditRouter(), new CircuitBreakerRegistry(),
                new RateLimiterRegistry(), new CompositeAuditListener(List.of(recording)),
                List.of(mask), store, null);
        store.save(AuditTask.pending("t1", new AsyncTaskRef("task-1", "d1"), "nickname", "fake"));

        AuditResult first = template.query("t1");
        // 终态迁移后再次读取走缓存
        AuditResult cached = template.query("t1");

        // 返回值、落库值、事件值统一脱敏，同一 taskId 每次读取形状一致
        assertEquals("***", first.hits().get(0).evidence());
        assertEquals("***", cached.hits().get(0).evidence());
        assertEquals("***", store.find("t1").orElseThrow().result().hits().get(0).evidence());
    }

    @Test
    void auditSkipsProvidersNotSupportingContentForm() {
        StubOperation urlOnly = new StubOperation("fake", 0) {
            @Override
            public AuditCapabilities capabilities() {
                return AuditCapabilities.builder().contentTypes(ContentType.TEXT).sync(true).url(true).build();
            }
        };
        DefaultContentSafeTemplate template = templateOf(providerOf(urlOnly));

        AuditRequest request = AuditRequest.builder()
                .scene(Scenes.NICKNAME)
                .type(ContentType.TEXT)
                .content(ContentRef.base64("5L2g5aW977yM5LiW55WM"))
                .dataId("d-1")
                .build();

        ProviderNotAvailableException ex = assertThrows(ProviderNotAvailableException.class,
                () -> template.audit(request));

        // 能力过滤前置于路由：内容形态不被支持的操作不进入候选，既不调用也不消耗熔断计数
        assertEquals(0, urlOnly.calls());
        assertTrue(ex.getMessage().contains("no provider supports"));
        assertTrue(events.stream().noneMatch(e -> "PROVIDER_DEGRADED".equals(e.eventType())));
    }

    @Test
    void submitAsyncWithoutAsyncProviderFailsFast() {
        DefaultContentSafeTemplate template = templateOf(providerOf(new StubOperation("fake", 0)));

        AuditRequest request = AuditRequest.builder()
                .scene(Scenes.NICKNAME)
                .type(ContentType.TEXT)
                .content(ContentRef.text("hello"))
                .dataId("d-1")
                .build();

        ProviderNotAvailableException ex = assertThrows(ProviderNotAvailableException.class,
                () -> template.submitAsync(request));

        assertTrue(ex.getMessage().contains("no async provider supports"));
    }

    @Test
    void submitAsyncReusesActiveTaskForSameDataId() {
        InMemoryAuditTaskStore store = new InMemoryAuditTaskStore();
        int[] submits = {0};
        StubOperation async = new StubOperation("fake", 0) {
            @Override
            public AuditCapabilities capabilities() {
                return AuditCapabilities.builder().contentTypes(ContentType.TEXT)
                        .sync(true).asyncQuery(true).build();
            }

            @Override
            public AsyncTaskRef submitAsync(AuditRequest request) {
                submits[0]++;
                return new AsyncTaskRef("task-" + submits[0], request.dataId());
            }
        };
        DefaultContentSafeTemplate template = new DefaultContentSafeTemplate(scenes,
                List.of(providerOf(async)), new PriorityAuditRouter(), new CircuitBreakerRegistry(),
                new RateLimiterRegistry(), new CompositeAuditListener(List.of(recording)),
                List.of(new DefaultEvidenceRedactor()), store, null);
        AuditRequest request = AuditRequest.builder()
                .scene(Scenes.NICKNAME)
                .type(ContentType.TEXT)
                .content(ContentRef.text("hello"))
                .dataId("biz-order-1")
                .build();

        String first = template.submitAsync(request);
        // 调用方超时重试是幂等提交的高频来源：同 dataId 活跃任务存在时复用既有 taskId，
        // 云侧不产生第二个计费任务
        String second = template.submitAsync(request);

        assertEquals(first, second);
        assertEquals(1, submits[0]);
    }

    @Test
    void submitAsyncCreatesNewTaskAfterTerminalTask() {
        InMemoryAuditTaskStore store = new InMemoryAuditTaskStore();
        int[] submits = {0};
        StubOperation async = new StubOperation("fake", 0) {
            @Override
            public AuditCapabilities capabilities() {
                return AuditCapabilities.builder().contentTypes(ContentType.TEXT)
                        .sync(true).asyncQuery(true).build();
            }

            @Override
            public AsyncTaskRef submitAsync(AuditRequest request) {
                submits[0]++;
                return new AsyncTaskRef("task-" + submits[0], request.dataId());
            }
        };
        DefaultContentSafeTemplate template = new DefaultContentSafeTemplate(scenes,
                List.of(providerOf(async)), new PriorityAuditRouter(), new CircuitBreakerRegistry(),
                new RateLimiterRegistry(), new CompositeAuditListener(List.of(recording)),
                List.of(new DefaultEvidenceRedactor()), store, null);
        AuditRequest request = AuditRequest.builder()
                .scene(Scenes.NICKNAME)
                .type(ContentType.TEXT)
                .content(ContentRef.text("hello"))
                .dataId("biz-order-1")
                .build();
        String first = template.submitAsync(request);
        store.markTerminal(first, TaskStatus.SUCCEEDED,
                AuditResult.builder().ok(true).suggestion(RiskSuggestion.PASS).build());

        // 已终态任务不参与复用：业务重新提交同一 dataId 应创建新任务
        String second = template.submitAsync(request);

        assertTrue(!first.equals(second));
        assertEquals(2, submits[0]);
    }

    @Test
    void allProvidersFailedExposesSanitizedMessagesAndCause() {
        StubOperation first = new StubOperation("fake-a", 0);
        StubOperation second = new StubOperation("fake-a", 1);
        first.failWith(new ProviderCallException(
                "upstream 500, token abcdef1234567890abcdef1234 expired"));
        second.failWith(new ProviderCallException("gateway timeout"));
        DefaultContentSafeTemplate template = templateOf(providerOf(first, second));

        AllProvidersFailedException ex = assertThrows(AllProvidersFailedException.class,
                () -> template.text(Scenes.NICKNAME, "你好"));

        // 失败明细带供应商 key 与净化后的 message，根因链保留最后一次底层异常
        assertTrue(ex.getMessage().contains("fake-a"));
        assertTrue(ex.getMessage().contains("gateway timeout"));
        assertTrue(ex.getMessage().contains("***"));
        assertTrue(!ex.getMessage().contains("abcdef1234567890abcdef1234"));
        assertTrue(ex.getCause() instanceof ProviderCallException);
    }

    @Test
    void builderProducesWorkingTemplate() {
        DefaultContentSafeTemplate template = DefaultContentSafeTemplate.builder()
                .sceneRegistry(scenes)
                .providers(List.of(providerOf(new StubOperation("fake", 0))))
                .router(new PriorityAuditRouter())
                .breakers(new CircuitBreakerRegistry())
                .limiters(new RateLimiterRegistry())
                .listener(new CompositeAuditListener(List.of(recording)))
                .redactors(List.of(new DefaultEvidenceRedactor()))
                .taskStore(new InMemoryAuditTaskStore())
                .build();

        assertTrue(template.text(Scenes.NICKNAME, "你好").pass());
    }

    @Test
    void queryRoutesByStoredPlanKeyNotProvider() {
        boolean[] queried = {false, false};
        StubOperation imageAsync = asyncQueryStub("fake", 0, "imageAsync", () -> queried[0] = true);
        StubOperation textAsync = asyncQueryStub("fake", 1, "textAsync", () -> queried[1] = true);
        InMemoryAuditTaskStore store = new InMemoryAuditTaskStore();
        store.save(AuditTask.pending("t1", new AsyncTaskRef("fake", "remote-1"),
                Scenes.NICKNAME, "fake", "fake:textAsync"));
        DefaultContentSafeTemplate template = DefaultContentSafeTemplate.builder()
                .sceneRegistry(scenes)
                .providers(List.of(providerOf(imageAsync, textAsync)))
                .router(new PriorityAuditRouter())
                .breakers(new CircuitBreakerRegistry())
                .limiters(new RateLimiterRegistry())
                .listener(new CompositeAuditListener(List.of(recording)))
                .taskStore(store)
                .build();

        template.query("t1");

        // 同一供应商挂多个异步操作时按 planKey 精确路由，不能张冠李戴
        assertTrue(queried[1]);
        assertTrue(!queried[0]);
    }

    @Test
    void queryFallsBackToProviderWhenTaskHasNoPlanKey() {
        boolean[] queried = {false};
        StubOperation legacy = asyncQueryStub("fake", 0, "imageAsync", () -> queried[0] = true);
        InMemoryAuditTaskStore store = new InMemoryAuditTaskStore();
        store.save(AuditTask.pending("t1", new AsyncTaskRef("fake", "remote-1"),
                Scenes.NICKNAME, "fake"));
        DefaultContentSafeTemplate template = DefaultContentSafeTemplate.builder()
                .sceneRegistry(scenes)
                .providers(List.of(providerOf(legacy)))
                .router(new PriorityAuditRouter())
                .breakers(new CircuitBreakerRegistry())
                .limiters(new RateLimiterRegistry())
                .listener(new CompositeAuditListener(List.of(recording)))
                .taskStore(store)
                .build();

        assertTrue(template.query("t1").pass());
        assertTrue(queried[0]);
    }

    /** ASYNC_QUERY 模式的探测桩，query 被调用时置位 probe */
    private static StubOperation asyncQueryStub(String provider, int order, String operation,
                                                Runnable probe) {
        return new StubOperation(provider, order) {
            @Override
            public ProviderPlan plan() {
                return new ProviderPlan(provider, "p", "1", operation, null,
                        AuditMode.ASYNC_QUERY, null, null);
            }

            @Override
            public AuditCapabilities capabilities() {
                return AuditCapabilities.builder().contentTypes(ContentType.TEXT).asyncQuery(true).build();
            }

            @Override
            public AuditResult query(AsyncTaskRef ref) {
                probe.run();
                return AuditResult.builder()
                        .ok(true)
                        .suggestion(RiskSuggestion.PASS)
                        .finalResult(true)
                        .provider(provider)
                        .build();
            }
        };
    }

    @Test
    void auditRejectsNonHttpUrlBeforeRouting() {
        StubOperation op = new StubOperation("fake", 0);
        DefaultContentSafeTemplate template = templateOf(providerOf(op));
        AuditRequest request = AuditRequest.image(Scenes.AVATAR, ContentRef.url("file:///etc/passwd"));

        assertThrows(InvalidRequestException.class, () -> template.audit(request));
        assertEquals(0, op.calls());
    }

    @Test
    void auditRejectsBlankContent() {
        DefaultContentSafeTemplate template = templateOf(providerOf(new StubOperation("fake", 0)));

        assertThrows(InvalidRequestException.class,
                () -> template.audit(AuditRequest.text(Scenes.NICKNAME, "   ")));
    }

    @Test
    void auditRejectsEmptyBytesContent() {
        DefaultContentSafeTemplate template = templateOf(providerOf(new StubOperation("fake", 0)));

        assertThrows(InvalidRequestException.class,
                () -> template.audit(AuditRequest.image(Scenes.AVATAR, ContentRef.bytes(new byte[0]))));
    }

    @Test
    void handleCallbackWithoutDispatcherThrowsUnsupportedFeature() {
        DefaultContentSafeTemplate template = templateOf(providerOf(new StubOperation("fake", 0)));

        assertThrows(UnsupportedFeatureException.class,
                () -> template.handleCallback(new CallbackRequest("fake", Map.of(), "{}")));
    }
}
