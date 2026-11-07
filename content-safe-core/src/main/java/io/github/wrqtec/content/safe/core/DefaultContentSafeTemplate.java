package io.github.wrqtec.content.safe.core;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import io.github.wrqtec.content.safe.api.AuditRequest;
import io.github.wrqtec.content.safe.api.AuditResult;
import io.github.wrqtec.content.safe.api.CallbackRequest;
import io.github.wrqtec.content.safe.api.CallbackResult;
import io.github.wrqtec.content.safe.api.ContentSafeTemplate;
import io.github.wrqtec.content.safe.api.ReviewResolution;
import io.github.wrqtec.content.safe.api.RiskHit;
import io.github.wrqtec.content.safe.api.enums.AuditSource;
import io.github.wrqtec.content.safe.api.enums.ReviewDecision;
import io.github.wrqtec.content.safe.api.enums.RiskSuggestion;
import io.github.wrqtec.content.safe.api.exception.AllProvidersFailedException;
import io.github.wrqtec.content.safe.api.exception.InvalidRequestException;
import io.github.wrqtec.content.safe.api.exception.ProviderCallException;
import io.github.wrqtec.content.safe.api.exception.ProviderNotAvailableException;
import io.github.wrqtec.content.safe.api.util.Asserts;
import io.github.wrqtec.content.safe.model.AsyncTaskRef;
import io.github.wrqtec.content.safe.model.AuditTask;
import io.github.wrqtec.content.safe.model.SceneDefinition;
import io.github.wrqtec.content.safe.model.enums.AuditMode;
import io.github.wrqtec.content.safe.model.enums.TaskStatus;
import io.github.wrqtec.content.safe.model.event.AuditEvent;
import io.github.wrqtec.content.safe.model.event.ProviderDegradedEvent;
import io.github.wrqtec.content.safe.model.event.ProviderThrottledEvent;
import io.github.wrqtec.content.safe.model.event.ReviewEvent;
import io.github.wrqtec.content.safe.model.event.RiskHitEvent;
import io.github.wrqtec.content.safe.core.callback.CallbackDispatcher;
import io.github.wrqtec.content.safe.core.degrade.CircuitBreaker;
import io.github.wrqtec.content.safe.core.degrade.CircuitBreakerRegistry;
import io.github.wrqtec.content.safe.core.limiter.RateLimiter;
import io.github.wrqtec.content.safe.core.limiter.RateLimiterRegistry;
import io.github.wrqtec.content.safe.spi.AuditArbitrationPolicy;
import io.github.wrqtec.content.safe.spi.AuditArbitrationSelector;
import io.github.wrqtec.content.safe.spi.AuditListener;
import io.github.wrqtec.content.safe.spi.AuditOperation;
import io.github.wrqtec.content.safe.spi.AuditOperationProvider;
import io.github.wrqtec.content.safe.spi.AuditRouter;
import io.github.wrqtec.content.safe.spi.AuditTaskStore;
import io.github.wrqtec.content.safe.spi.EvidenceRedactor;
import io.github.wrqtec.content.safe.spi.ReviewResultSink;
import lombok.extern.slf4j.Slf4j;

/**
 * 默认编排实现：场景解析 → 能力过滤 → 路由 → 熔断 → 执行（失败转移）→ 脱敏 → 事件
 * 不感知任何具体供应商；所有供应商差异收敛在 provider 模块与 SPI 实现内
 *
 * @author L.David
 */
@Slf4j
public class DefaultContentSafeTemplate implements ContentSafeTemplate {

    /** 脱敏器通配 provider：匹配任意供应商 */
    private static final String ANY_PROVIDER = "*";

    /** 默认异步轮询间隔（接口未显式传入时） */
    private static final Duration DEFAULT_POLL_INTERVAL = Duration.ofSeconds(2);

    /** 默认异步等待超时（接口未显式传入时） */
    private static final Duration DEFAULT_POLL_TIMEOUT = Duration.ofMinutes(5);

    private final SceneRegistry sceneRegistry;
    private final List<AuditOperationProvider> providers;
    private final AuditRouter router;
    private final CircuitBreakerRegistry breakers;
    private final RateLimiterRegistry limiters;
    private final AuditListener listener;
    private final List<EvidenceRedactor> redactors;
    private final AuditTaskStore taskStore;
    private final ReviewResultSink reviewSink;
    private final CallbackDispatcher callbackDispatcher;
    private final AuditArbitrationSelector arbitrationSelector;
    private final Duration pollInterval;
    private final Duration pollTimeout;

    /**
     * @param sceneRegistry      场景注册表
     * @param providers          供应商操作提供器列表，可为 null（视为空）
     * @param router             路由器
     * @param breakers           熔断器注册表
     * @param limiters           限流器注册表
     * @param listener           事件监听器
     * @param redactors          证据脱敏器列表，可为 null（视为空）
     * @param taskStore          异步任务存储
     * @param callbackDispatcher 回调分发器，可为 null（不支持回调）
     */
    public DefaultContentSafeTemplate(SceneRegistry sceneRegistry,
                                      List<AuditOperationProvider> providers,
                                      AuditRouter router,
                                      CircuitBreakerRegistry breakers,
                                      RateLimiterRegistry limiters,
                                      AuditListener listener,
                                      List<EvidenceRedactor> redactors,
                                      AuditTaskStore taskStore,
                                      CallbackDispatcher callbackDispatcher) {
        this(sceneRegistry, providers, router, breakers, limiters, listener,
                redactors, taskStore, null, callbackDispatcher);
    }

    /**
     * @param reviewSink 人审终态回写出口，可为 null（仅做终态归一与事件发布）
     * @see #DefaultContentSafeTemplate(SceneRegistry, List, AuditRouter, CircuitBreakerRegistry,
     * RateLimiterRegistry, AuditListener, List, AuditTaskStore, ReviewResultSink, CallbackDispatcher,
     * AuditArbitrationSelector, Duration, Duration)
     */
    public DefaultContentSafeTemplate(SceneRegistry sceneRegistry,
                                      List<AuditOperationProvider> providers,
                                      AuditRouter router,
                                      CircuitBreakerRegistry breakers,
                                      RateLimiterRegistry limiters,
                                      AuditListener listener,
                                      List<EvidenceRedactor> redactors,
                                      AuditTaskStore taskStore,
                                      ReviewResultSink reviewSink,
                                      CallbackDispatcher callbackDispatcher) {
        this(sceneRegistry, providers, router, breakers, limiters, listener,
                redactors, taskStore, reviewSink, callbackDispatcher, null,
                DEFAULT_POLL_INTERVAL, DEFAULT_POLL_TIMEOUT);
    }

    /**
     * 全参构造
     *
     * @param arbitrationSelector 仲裁策略选择器，可为 null（全部场景走主备 failover）
     * @param pollInterval        {@link #submitAndWait(AuditRequest)} 默认轮询间隔，非正数时回退 2 秒
     * @param pollTimeout         {@link #submitAndWait(AuditRequest)} 默认等待超时，非正数时回退 5 分钟
     */
    public DefaultContentSafeTemplate(SceneRegistry sceneRegistry,
                                      List<AuditOperationProvider> providers,
                                      AuditRouter router,
                                      CircuitBreakerRegistry breakers,
                                      RateLimiterRegistry limiters,
                                      AuditListener listener,
                                      List<EvidenceRedactor> redactors,
                                      AuditTaskStore taskStore,
                                      ReviewResultSink reviewSink,
                                      CallbackDispatcher callbackDispatcher,
                                      AuditArbitrationSelector arbitrationSelector,
                                      Duration pollInterval,
                                      Duration pollTimeout) {
        this.sceneRegistry = Asserts.notNull(sceneRegistry, "sceneRegistry");
        this.providers = providers == null ? List.of() : List.copyOf(providers);
        this.router = Asserts.notNull(router, "router");
        this.breakers = Asserts.notNull(breakers, "breakers");
        this.limiters = Asserts.notNull(limiters, "limiters");
        this.listener = Asserts.notNull(listener, "listener");
        this.redactors = redactors == null ? List.of() : List.copyOf(redactors);
        this.taskStore = Asserts.notNull(taskStore, "taskStore");
        this.reviewSink = reviewSink;
        this.callbackDispatcher = callbackDispatcher;
        this.arbitrationSelector = arbitrationSelector;
        this.pollInterval = pollInterval != null && !pollInterval.isNegative() && !pollInterval.isZero()
                ? pollInterval : DEFAULT_POLL_INTERVAL;
        this.pollTimeout = pollTimeout != null && !pollTimeout.isNegative() && !pollTimeout.isZero()
                ? pollTimeout : DEFAULT_POLL_TIMEOUT;
    }

    @Override
    public AuditResult audit(AuditRequest request) {
        Asserts.notNull(request, "request");
        SceneDefinition scene = sceneRegistry.require(request.scene());
        if (scene.type() != request.type()) {
            throw new InvalidRequestException(
                    "scene " + scene.code() + " expects " + scene.type() + " but got " + request.type());
        }

        List<AuditOperation> remaining = new ArrayList<>(candidates(scene));
        // 异步操作不参与同步路由：audit() 在异步操作上始终抛 UnsupportedFeatureException，
        // 若混入候选会被熔断器记为失败导致误熔断
        remaining.removeIf(op -> op.plan().mode() == AuditMode.ASYNC_QUERY
                || op.plan().mode() == AuditMode.ASYNC_CALLBACK);
        if (remaining.isEmpty()) {
            throw new ProviderNotAvailableException("no provider configured for scene: " + scene.code());
        }

        AuditArbitrationPolicy policy = arbitrationSelector == null
                ? null : arbitrationSelector.policyFor(scene.code());
        if (policy != null) {
            return auditWithArbitration(scene, request, remaining, policy);
        }

        List<String> failures = new ArrayList<>();
        while (!remaining.isEmpty()) {
            AuditOperation operation = router.route(request, remaining);
            remaining.remove(operation);
            AuditResult result = tryExecute(operation, scene, request, failures);
            if (result != null) {
                return result;
            }
        }
        throw new AllProvidersFailedException(
                "all providers failed for scene " + scene.code() + ": " + String.join("; ", failures));
    }

    /**
     * 仲裁模式：候选操作全量采集（每家各调用一次，成本 = 全部候选），任一成功即入集合；
     * 全部失败抛 {@link AllProvidersFailedException}，否则交由策略仲裁合并。
     * 每个供应商结果先发布各自事件，仲裁结果再以 source=ARBITRATION 发布
     */
    private AuditResult auditWithArbitration(SceneDefinition scene, AuditRequest request,
                                             List<AuditOperation> operations, AuditArbitrationPolicy policy) {
        List<String> failures = new ArrayList<>();
        List<AuditResult> results = new ArrayList<>();
        for (AuditOperation operation : operations) {
            AuditResult result = tryExecute(operation, scene, request, failures);
            if (result != null) {
                results.add(result);
            }
        }
        if (results.isEmpty()) {
            throw new AllProvidersFailedException(
                    "all providers failed for scene " + scene.code() + ": " + String.join("; ", failures));
        }
        AuditResult arbitrated = withSource(policy.arbitrate(results), AuditSource.ARBITRATION);
        log.debug("audit arbitrated: scene={}, policy={}, providers={}, suggestion={}",
                scene.code(), policy.name(), results.size(), arbitrated.suggestion());
        publishAuditEvents(scene.code(), arbitrated);
        return arbitrated;
    }

    /**
     * 单次尝试：限流 → 熔断 → 执行 → 脱敏 → 发布事件。
     * 成功返回脱敏结果；被限流/熔断或调用失败返回 null 并登记 failures（含调试日志）
     */
    private AuditResult tryExecute(AuditOperation operation, SceneDefinition scene,
                                   AuditRequest request, List<String> failures) {
        String planKey = operation.plan().planKey();
        // 限流先于熔断：避免在半开状态下因本地限流跳过而泄漏探测名额
        RateLimiter.Permit permit = limiters.get(planKey, operation.plan().limits()).tryAcquire();
        if (!permit.acquired()) {
            failures.add(planKey + ": rate limited");
            listener.onEvent(new ProviderThrottledEvent(operation.plan().provider(),
                    operation.plan().operation(), "rate limited", null));
            log.debug("provider {} throttled on scene {}, fail over to next", planKey, scene.code());
            return null;
        }
        CircuitBreaker breaker = breakers.get(planKey);
        if (!breaker.tryAcquire()) {
            permit.release();
            failures.add(planKey + ": circuit open");
            return null;
        }
        try {
            AuditResult result = operation.audit(request);
            breaker.onSuccess();
            AuditResult redacted = redact(result, operation.plan().provider());
            publishAuditEvents(scene.code(), redacted);
            // 成功路径可观测：只含路由与结论元数据，不含用户内容原文
            log.debug("audit success: scene={}, provider={}, suggestion={}, hits={}, cost={}",
                    scene.code(), planKey, redacted.suggestion(), redacted.hits().size(), redacted.cost());
            return redacted;
        } catch (RuntimeException e) {
            breaker.onFailure();
            failures.add(planKey + ": " + e.getClass().getSimpleName());
            listener.onEvent(new ProviderDegradedEvent(operation.plan().provider(),
                    operation.plan().operation(), e.getClass().getSimpleName(), null));
            log.warn("provider {} degraded on scene {}, fail over to next",
                    operation.plan().planKey(), scene.code(), e);
            return null;
        } finally {
            permit.release();
        }
    }

    @Override
    public AuditResult submitAndWait(AuditRequest request) {
        return ContentSafeTemplate.super.submitAndWait(request, pollInterval, pollTimeout);
    }

    @Override
    public String submitAsync(AuditRequest request) {
        Asserts.notNull(request, "request");
        SceneDefinition scene = sceneRegistry.require(request.scene());
        List<AuditOperation> async = candidates(scene).stream()
                .filter(op -> op.capabilities().asyncQuery() || op.capabilities().asyncCallback())
                .filter(op -> op.supports(request))
                .toList();
        AuditOperation operation = router.route(request, async);

        String planKey = operation.plan().planKey();
        RateLimiter.Permit permit = limiters.get(planKey, operation.plan().limits()).tryAcquire();
        if (!permit.acquired()) {
            listener.onEvent(new ProviderThrottledEvent(operation.plan().provider(),
                    operation.plan().operation(), "rate limited", null));
            throw new ProviderNotAvailableException("rate limited for " + planKey);
        }
        CircuitBreaker breaker = breakers.get(planKey);
        if (!breaker.tryAcquire()) {
            permit.release();
            throw new ProviderNotAvailableException("circuit open for " + planKey);
        }
        try {
            AsyncTaskRef ref = operation.submitAsync(request);
            breaker.onSuccess();
            String taskId = UUID.randomUUID().toString();
            taskStore.save(AuditTask.pending(taskId, ref, scene.code(), operation.plan().provider()));
            return taskId;
        } catch (RuntimeException e) {
            breaker.onFailure();
            throw e;
        } finally {
            permit.release();
        }
    }

    @Override
    public AuditResult query(String taskId) {
        Asserts.notBlank(taskId, "taskId");
        AuditTask task = taskStore.find(taskId)
                .orElseThrow(() -> new InvalidRequestException("unknown taskId: " + taskId));
        if (task.status().isTerminal()) {
            return task.result();
        }
        AuditOperation operation = findAsyncQueryOperation(task);
        AuditResult result = operation.query(task.ref());
        if (result.finalResult()) {
            if (taskStore.markTerminal(taskId, TaskStatus.SUCCEEDED, result)) {
                AuditResult redacted = redact(result, operation.plan().provider());
                publishAuditEvents(task.scene(), redacted);
                return redacted;
            }
            // 并发下已被迁移，返回存储中的终态结果
            return taskStore.find(taskId).map(AuditTask::result).orElse(result);
        }
        return result;
    }

    @Override
    public CallbackResult handleCallback(CallbackRequest callback) {
        Asserts.notNull(callback, "callback");
        Asserts.notNull(callbackDispatcher, "callbackDispatcher");
        return callbackDispatcher.dispatch(callback);
    }

    @Override
    public AuditResult resolveReview(ReviewResolution resolution) {
        Asserts.notNull(resolution, "resolution");
        AuditResult manualResult = manualResultOf(resolution);
        if (reviewSink != null) {
            // 回写先行：sink 失败在任务终态迁移前暴露，业务可整体重试
            reviewSink.resolve(resolution);
        }
        if (resolution.taskId() != null) {
            AuditTask task = taskStore.find(resolution.taskId())
                    .orElseThrow(() -> new InvalidRequestException("unknown taskId: " + resolution.taskId()));
            if (!taskStore.markTerminal(resolution.taskId(), TaskStatus.SUCCEEDED, manualResult)) {
                throw new InvalidRequestException("task already terminal: " + resolution.taskId());
            }
        }
        publishAuditEvents(resolution.scene(), manualResult);
        return manualResult;
    }

    private List<AuditOperation> candidates(SceneDefinition scene) {
        return providers.stream()
                .flatMap(p -> p.operations(scene).stream())
                .toList();
    }

    private AuditOperation findAsyncQueryOperation(AuditTask task) {
        SceneDefinition scene = sceneRegistry.require(task.scene());
        return candidates(scene).stream()
                .filter(op -> op.plan().provider().equals(task.provider()))
                .filter(op -> op.capabilities().asyncQuery())
                .findFirst()
                .orElseThrow(() -> new ProviderNotAvailableException(
                        "no async-query operation for provider: " + task.provider()));
    }

    /** 仅替换来源标记，其余字段原样保留（仲裁合并结果标 ARBITRATION） */
    private AuditResult withSource(AuditResult result, AuditSource source) {
        return new AuditResult(result.ok(), result.suggestion(), result.finalResult(), source,
                result.hits(), result.requestId(), result.dataId(), result.provider(), result.product(),
                result.service(), result.cost(), result.raw());
    }

    private AuditResult redact(AuditResult result, String provider) {
        if (result == null || result.hits().isEmpty()) {
            return result;
        }
        EvidenceRedactor redactor = redactors.stream()
                .filter(r -> r.provider().equals(provider) || ANY_PROVIDER.equals(r.provider()))
                .findFirst()
                .orElse(null);
        if (redactor == null) {
            return result;
        }
        List<RiskHit> redactedHits = result.hits().stream().map(redactor::redact).toList();
        return new AuditResult(result.ok(), result.suggestion(), result.finalResult(), result.source(),
                redactedHits, result.requestId(), result.dataId(), result.provider(), result.product(),
                result.service(), result.cost(), result.raw());
    }

    /** 决议 -> 终态结果：source=MANUAL、finalResult=true，回链机审 dataId 供业务对账 */
    private AuditResult manualResultOf(ReviewResolution resolution) {
        return AuditResult.builder()
                .ok(true)
                .suggestion(resolution.decision() == ReviewDecision.APPROVE
                        ? RiskSuggestion.PASS : RiskSuggestion.REJECT)
                .finalResult(true)
                .source(AuditSource.MANUAL)
                .dataId(resolution.dataId())
                .build();
    }

    private void publishAuditEvents(String scene, AuditResult result) {
        listener.onEvent(new AuditEvent(scene, result, null));
        if (!result.pass() && !result.hits().isEmpty()
                && result.suggestion() != RiskSuggestion.ERROR) {
            listener.onEvent(new RiskHitEvent(scene, result.dataId(), result.hits(), null));
        }
        if (result.suggestion() == RiskSuggestion.REVIEW) {
            listener.onEvent(new ReviewEvent(scene, result.dataId(), result, null));
        }
    }
}
