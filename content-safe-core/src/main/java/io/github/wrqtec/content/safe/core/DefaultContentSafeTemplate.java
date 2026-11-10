package io.github.wrqtec.content.safe.core;

import java.net.URI;
import java.net.URISyntaxException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import io.github.wrqtec.content.safe.api.AuditRequest;
import io.github.wrqtec.content.safe.api.AuditResult;
import io.github.wrqtec.content.safe.api.CallbackRequest;
import io.github.wrqtec.content.safe.api.CallbackResult;
import io.github.wrqtec.content.safe.api.ContentRef;
import io.github.wrqtec.content.safe.api.ContentSafeTemplate;
import io.github.wrqtec.content.safe.api.ReviewResolution;
import io.github.wrqtec.content.safe.api.enums.AuditSource;
import io.github.wrqtec.content.safe.api.enums.ReviewDecision;
import io.github.wrqtec.content.safe.api.enums.RiskSuggestion;
import io.github.wrqtec.content.safe.api.exception.AllProvidersFailedException;
import io.github.wrqtec.content.safe.api.exception.InvalidRequestException;
import io.github.wrqtec.content.safe.api.exception.ProviderCallException;
import io.github.wrqtec.content.safe.api.exception.ProviderNotAvailableException;
import io.github.wrqtec.content.safe.api.exception.UnsupportedFeatureException;
import io.github.wrqtec.content.safe.api.util.Asserts;
import io.github.wrqtec.content.safe.model.AsyncTaskRef;
import io.github.wrqtec.content.safe.model.AuditTask;
import io.github.wrqtec.content.safe.model.SceneDefinition;
import io.github.wrqtec.content.safe.model.enums.AuditMode;
import io.github.wrqtec.content.safe.model.enums.TaskStatus;
import io.github.wrqtec.content.safe.model.event.ProviderDegradedEvent;
import io.github.wrqtec.content.safe.model.event.ProviderThrottledEvent;
import io.github.wrqtec.content.safe.core.callback.CallbackDispatcher;
import io.github.wrqtec.content.safe.core.degrade.CircuitBreaker;
import io.github.wrqtec.content.safe.core.degrade.CircuitBreakerRegistry;
import io.github.wrqtec.content.safe.core.limiter.RateLimiter;
import io.github.wrqtec.content.safe.core.limiter.RateLimiterRegistry;
import io.github.wrqtec.content.safe.core.listener.AuditEventPublisher;
import io.github.wrqtec.content.safe.core.security.ErrorMessages;
import io.github.wrqtec.content.safe.core.security.EvidenceRedactors;
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
    private final AuditEventPublisher eventPublisher;
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
     * @deprecated 改用 {@link #builder()}；本构造器仅为兼容保留，不再扩展新参数
     */
    @Deprecated(since = "1.1.0", forRemoval = false)
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
     * @deprecated 改用 {@link #builder()}；本构造器仅为兼容保留，不再扩展新参数
     * @see #DefaultContentSafeTemplate(SceneRegistry, List, AuditRouter, CircuitBreakerRegistry,
     * RateLimiterRegistry, AuditListener, List, AuditTaskStore, ReviewResultSink, CallbackDispatcher,
     * AuditArbitrationSelector, Duration, Duration)
     */
    @Deprecated(since = "1.1.0", forRemoval = false)
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
     * 全参构造器：与 {@link #builder()} 全属性设置等价，是其委托落点，保持稳定不弃用。
     * 新增参数继续通过 Builder 暴露（命名参数、可演进），代码装配建议优先用 builder()
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
        this.eventPublisher = new AuditEventPublisher(this.listener);
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
        validateContent(request.content());
        SceneDefinition scene = sceneRegistry.require(request.scene());
        if (scene.type() != request.type()) {
            throw new InvalidRequestException(
                    "scene " + scene.code() + " expects " + scene.type() + " but got " + request.type());
        }

        List<AuditOperation> configured = candidates(scene);
        if (configured.isEmpty()) {
            throw new ProviderNotAvailableException("no provider configured for scene: " + scene.code());
        }
        List<AuditOperation> remaining = configured.stream()
                // 异步操作不参与同步路由：audit() 在异步操作上始终抛 UnsupportedFeatureException，
                // 若混入候选会被熔断器记为失败导致误熔断
                .filter(op -> op.plan().mode() != AuditMode.ASYNC_QUERY
                        && op.plan().mode() != AuditMode.ASYNC_CALLBACK)
                // 能力过滤前置于路由（与 submitAsync 一致）：内容形态不被支持的操作不进入候选，
                // 避免注定失败的调用消耗熔断计数；主备路由与仲裁路径拿到的都是已过滤集合
                .filter(op -> op.supports(request))
                .collect(Collectors.toCollection(ArrayList::new));
        if (remaining.isEmpty()) {
            throw new ProviderNotAvailableException("no provider supports scene " + scene.code()
                    + " for content " + request.content().type());
        }

        AuditArbitrationPolicy policy = arbitrationSelector == null
                ? null : arbitrationSelector.policyFor(scene.code());
        if (policy != null) {
            return auditWithArbitration(scene, request, remaining, policy);
        }

        List<String> failures = new ArrayList<>();
        RuntimeException lastFailure = null;
        while (!remaining.isEmpty()) {
            AuditOperation operation = router.route(request, remaining);
            remaining.remove(operation);
            Attempt attempt = tryExecute(operation, scene, request);
            if (attempt.result() != null) {
                return attempt.result();
            }
            failures.add(attempt.failure());
            if (attempt.cause() != null) {
                lastFailure = attempt.cause();
            }
        }
        // 最后一次底层异常挂为 cause，保留根因链
        throw new AllProvidersFailedException(
                "all providers failed for scene " + scene.code() + ": " + String.join("; ", failures),
                lastFailure);
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
        RuntimeException lastFailure = null;
        for (AuditOperation operation : operations) {
            Attempt attempt = tryExecute(operation, scene, request);
            if (attempt.result() != null) {
                results.add(attempt.result());
                continue;
            }
            failures.add(attempt.failure());
            if (attempt.cause() != null) {
                lastFailure = attempt.cause();
            }
        }
        if (results.isEmpty()) {
            throw new AllProvidersFailedException(
                    "all providers failed for scene " + scene.code() + ": " + String.join("; ", failures),
                    lastFailure);
        }
        AuditResult arbitrated = policy.arbitrate(results).withSource(AuditSource.ARBITRATION);
        log.debug("audit arbitrated: scene={}, policy={}, providers={}, suggestion={}",
                scene.code(), policy.name(), results.size(), arbitrated.suggestion());
        eventPublisher.publish(scene.code(), arbitrated);
        return arbitrated;
    }

    /**
     * 单次尝试结果：成功仅 {@code result} 非空；失败仅 {@code failure}（明细）非空，
     * {@code cause} 为底层异常（限流/熔断等本地拒绝时为 null）
     */
    private record Attempt(AuditResult result, String failure, RuntimeException cause) {

        static Attempt success(AuditResult result) {
            return new Attempt(result, null, null);
        }

        static Attempt failure(String failure, RuntimeException cause) {
            return new Attempt(null, failure, cause);
        }
    }

    /**
     * 单次尝试：限流 → 熔断 → 执行 → 脱敏 → 发布事件。
     * 成功返回 {@link Attempt#success}；被限流/熔断或调用失败返回 {@link Attempt#failure}（含调试日志）。
     * 失败明细只含异常类名与净化后的 message，不含用户内容
     */
    private Attempt tryExecute(AuditOperation operation, SceneDefinition scene, AuditRequest request) {
        String planKey = operation.plan().planKey();
        try {
            AuditResult result = executeGuarded(operation, scene, () -> operation.audit(request));
            AuditResult redacted = EvidenceRedactors.redactResult(redactors, result, operation.plan().provider());
            eventPublisher.publish(scene.code(), redacted);
            // 成功路径可观测：只含路由与结论元数据，不含用户内容原文
            log.debug("audit success: scene={}, provider={}, suggestion={}, hits={}, cost={}",
                    scene.code(), planKey, redacted.suggestion(), redacted.hits().size(), redacted.cost());
            return Attempt.success(redacted);
        } catch (GuardRejection e) {
            // 限流/熔断未放行：不计 provider 失败（熔断记账已在护栏外），转移到下一候选
            return Attempt.failure(planKey + ": " + e.reason(), null);
        } catch (RuntimeException e) {
            // provider 已对自身异常脱敏，此处再兜底净化一次，防止第三方 SDK 异常夹带敏感片段
            return Attempt.failure(planKey + ": " + e.getClass().getSimpleName()
                    + describeFailure(e), e);
        }
    }

    /**
     * 统一执行护栏：限流 → 熔断放行 → 执行 → 熔断记账。audit() 失败转移与
     * submitAsync 异常传播共用同一护栏，保证限流/熔断语义单点维护。
     * 限流先于熔断：避免在半开状态下因本地限流跳过而泄漏探测名额。
     * 限流/熔断未放行抛 {@link GuardRejection}，与 provider 自身异常区分
     */
    private <T> T executeGuarded(AuditOperation operation, SceneDefinition scene, Supplier<T> action) {
        String planKey = operation.plan().planKey();
        RateLimiter.Permit permit = limiters.get(planKey, operation.plan().limits()).tryAcquire();
        if (!permit.acquired()) {
            listener.onEvent(new ProviderThrottledEvent(operation.plan().provider(),
                    operation.plan().operation(), scene.code(), "rate limited", null));
            log.debug("provider {} throttled on scene {}", planKey, scene.code());
            throw new GuardRejection("rate limited", "rate limited for " + planKey);
        }
        CircuitBreaker breaker = breakers.get(planKey);
        if (!breaker.tryAcquire()) {
            permit.release();
            throw new GuardRejection("circuit open", "circuit open for " + planKey);
        }
        try {
            T value = action.get();
            breaker.onSuccess();
            return value;
        } catch (RuntimeException e) {
            breaker.onFailure();
            listener.onEvent(new ProviderDegradedEvent(operation.plan().provider(),
                    operation.plan().operation(), scene.code(), e.getClass().getSimpleName(), null));
            log.warn("provider {} degraded on scene {}", planKey, scene.code(), e);
            throw e;
        } finally {
            permit.release();
        }
    }

    /**
     * 护栏拒绝信号（限流/熔断未放行）。继承 {@link ProviderNotAvailableException}
     * 使 submitAsync 路径的对外语义不变；reason 供失败转移路径拼装明细
     */
    private static final class GuardRejection extends ProviderNotAvailableException {

        private final String reason;

        GuardRejection(String reason, String message) {
            super(message);
            this.reason = reason;
        }

        String reason() {
            return reason;
        }
    }

    /** 失败明细附加段：message 空返回空串，否则追加净化限长后的 message */
    private static String describeFailure(RuntimeException e) {
        String message = e.getMessage();
        if (message == null || message.isBlank()) {
            return "";
        }
        return ": " + ErrorMessages.sanitize(message);
    }

    /**
     * 最小公共输入治理：在任何 provider 之前统一拦截明显非法的内容引用。
     * 职责边界——公共形状校验（非空、URL scheme 白名单）在 core 只做一次；
     * 供应商特有的大小/像素/格式限制由各 provider 自行预检（它们最懂自己的 limits）。
     * 错误消息只描述问题，不回显内容值（URL 可能携带签名参数，见安全约束）
     */
    private static void validateContent(ContentRef content) {
        Asserts.notNull(content, "content");
        switch (content.type()) {
            case TEXT, BASE64, OBJECT_KEY -> Asserts.notBlank(content.value(), "content value");
            case BYTES -> {
                if (content.bytes() == null || content.bytes().length == 0) {
                    throw new InvalidRequestException("content bytes must not be empty");
                }
            }
            case URL -> validateUrl(content.value());
            default -> throw new InvalidRequestException("unsupported content type: " + content.type());
        }
    }

    /** URL 仅放行带主机的 http/https 绝对地址：scheme/主机合法性属于公共契约，供应商侧不再各自校验 */
    private static void validateUrl(String url) {
        Asserts.notBlank(url, "content url");
        URI uri;
        try {
            uri = new URI(url.trim());
        } catch (URISyntaxException e) {
            throw new InvalidRequestException("content url is not a valid URI");
        }
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        if (!scheme.equals("http") && !scheme.equals("https")) {
            throw new InvalidRequestException("content url scheme must be http/https");
        }
        if (uri.getHost() == null || uri.getHost().isBlank()) {
            throw new InvalidRequestException("content url is missing host");
        }
    }

    @Override
    public AuditResult submitAndWait(AuditRequest request) {
        return ContentSafeTemplate.super.submitAndWait(request, pollInterval, pollTimeout);
    }

    @Override
    public String submitAsync(AuditRequest request) {
        Asserts.notNull(request, "request");
        validateContent(request.content());
        SceneDefinition scene = sceneRegistry.require(request.scene());
        // 幂等去重：调用方显式传入的 dataId 即幂等键，活跃任务已存在时复用既有 taskId，
        // 避免超时重试在云侧产生第二个计费任务；快捷构造（text/image）自动生成 UUID，
        // 天然不受影响。dataId 未传时无法去重，直接提交
        if (request.dataId() != null && !request.dataId().isBlank()) {
            Optional<AuditTask> active = taskStore.findActiveByDataId(scene.code(), request.dataId());
            if (active.isPresent()) {
                AuditTask existing = active.get();
                log.debug("async submit deduplicated: scene={}, taskId={}", scene.code(), existing.taskId());
                return existing.taskId();
            }
        }
        List<AuditOperation> async = candidates(scene).stream()
                .filter(op -> op.capabilities().asyncQuery() || op.capabilities().asyncCallback())
                .filter(op -> op.supports(request))
                .toList();
        if (async.isEmpty()) {
            throw new ProviderNotAvailableException("no async provider supports scene " + scene.code()
                    + " for content " + request.content().type());
        }
        AuditOperation operation = router.route(request, async);

        String planKey = operation.plan().planKey();
        try {
            AsyncTaskRef ref = executeGuarded(operation, scene, () -> operation.submitAsync(request));
            String taskId = UUID.randomUUID().toString();
            taskStore.save(AuditTask.pending(taskId, ref, scene.code(),
                    operation.plan().provider(), planKey));
            return taskId;
        } catch (GuardRejection e) {
            // 对外语义不变：限流/熔断未放行抛 ProviderNotAvailableException（单操作路径无失败转移）
            throw new ProviderNotAvailableException(e.getMessage());
        }
    }

    @Override
    public AuditResult query(String taskId) {
        Asserts.notBlank(taskId, "taskId");
        AuditTask task = taskStore.find(taskId)
                .orElseThrow(() -> new InvalidRequestException("unknown taskId: " + taskId));
        if (task.status().isTerminal()) {
            // 存储的是脱敏后结果，缓存命中直接返回
            return task.result();
        }
        AuditOperation operation = findAsyncQueryOperation(task);
        // 脱敏先行：落库、事件、返回值统一使用脱敏副本，保证同一 taskId 每次读取形状一致
        AuditResult result = EvidenceRedactors.redactResult(redactors,
                operation.query(task.ref()), operation.plan().provider());
        if (result.finalResult()) {
            if (taskStore.markTerminal(taskId, TaskStatus.SUCCEEDED, result)) {
                eventPublisher.publish(task.scene(), result);
                return result;
            }
            // 并发下已被其他线程迁移，返回存储中的终态结果；orElse 兜底的是
            // "实现会驱逐终态记录"的 store（如按读取消费/终态 TTL 清理），此时返回本次结果
            return taskStore.find(taskId).map(AuditTask::result).orElse(result);
        }
        return result;
    }

    @Override
    public CallbackResult handleCallback(CallbackRequest callback) {
        Asserts.notNull(callback, "callback");
        if (callbackDispatcher == null) {
            // 未装配回调分发器是功能未启用，不是参数断言失败
            throw new UnsupportedFeatureException(
                    "callback dispatching is not configured (content.safe.callback not enabled)");
        }
        return callbackDispatcher.dispatch(callback);
    }

    @Override
    public AuditResult resolveReview(ReviewResolution resolution) {
        Asserts.notNull(resolution, "resolution");
        AuditResult manualResult = manualResultOf(resolution);
        if (resolution.taskId() != null) {
            AuditTask task = taskStore.find(resolution.taskId())
                    .orElseThrow(() -> new InvalidRequestException("unknown taskId: " + resolution.taskId()));
            if (task.status().isTerminal()) {
                // 终态预检先行：任务已被处理时直接拒绝，避免 sink 副作用已发出却报错、业务不可整体重试
                throw new InvalidRequestException("task already terminal: " + resolution.taskId());
            }
        }
        if (reviewSink != null) {
            // 回写先行：sink 失败在任务终态迁移前暴露，业务可整体重试；
            // 预检与 markTerminal 之间仍存在极小竞态窗口，严格并发场景要求 sink 幂等
            reviewSink.resolve(resolution);
        }
        if (resolution.taskId() != null) {
            if (!taskStore.markTerminal(resolution.taskId(), TaskStatus.SUCCEEDED, manualResult)) {
                throw new InvalidRequestException("task already terminal (raced): " + resolution.taskId());
            }
        }
        eventPublisher.publish(resolution.scene(), manualResult);
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
                // 任务记录了提交操作的 planKey 时按它精确路由（同一供应商可挂多个异步操作，
                // 仅按 provider 匹配会张冠李戴）；operation 为空回退 provider 匹配不是死代码——
                // 1.0.0 已发布（tag v1.0.0，2026-11-07），其 AuditTask 无 operation 字段、
                // pending 仅 4 参（git show v1.0.0:content-safe-model/src/main/java/io/github/wrqtec/
                // content/safe/model/AuditTask.java 可验证），
                // 该版本落库的 PENDING 任务升级到本版本后 operation() 为 null，只能回退 provider 匹配；
                // 存量任务随终态自然消亡，此分支可在确认无 1.0.0 存量后（≥2.0.0）收紧
                .filter(op -> task.operation() != null && !task.operation().isBlank()
                        ? op.plan().planKey().equals(task.operation())
                        : op.plan().provider().equals(task.provider()))
                .filter(op -> op.capabilities().asyncQuery())
                .findFirst()
                .orElseThrow(() -> new ProviderNotAvailableException(task.operation() != null
                        ? "no async-query operation for planKey: " + task.operation()
                        : "no async-query operation for provider: " + task.provider()));
    }

    /** 决议 -> 终态结果：source=MANUAL、finalResult=true，回链机审 dataId 供业务对账。
     * cost=ZERO：非机审调用无外部耗时，保证指标侧取值恒非 null */
    private AuditResult manualResultOf(ReviewResolution resolution) {
        return AuditResult.builder()
                .ok(true)
                .suggestion(resolution.decision() == ReviewDecision.APPROVE
                        ? RiskSuggestion.PASS : RiskSuggestion.REJECT)
                .finalResult(true)
                .source(AuditSource.MANUAL)
                .dataId(resolution.dataId())
                .cost(Duration.ZERO)
                .build();
    }

    /**
     * 创建构建器（推荐的装配方式）。
     * 必填项：{@code sceneRegistry/providers/router/breakers/limiters/listener/taskStore}，
     * build() 委托构造器统一做参数校验；可选项缺省时与旧构造器语义一致
     *
     * @return 构建器
     */
    public static Builder builder() {
        return new Builder();
    }

    /** {@link DefaultContentSafeTemplate} 构建器：可选属性链式设置，替代多参构造器重载 */
    public static final class Builder {

        private SceneRegistry sceneRegistry;
        private List<AuditOperationProvider> providers;
        private AuditRouter router;
        private CircuitBreakerRegistry breakers;
        private RateLimiterRegistry limiters;
        private AuditListener listener;
        private List<EvidenceRedactor> redactors;
        private AuditTaskStore taskStore;
        private ReviewResultSink reviewSink;
        private CallbackDispatcher callbackDispatcher;
        private AuditArbitrationSelector arbitrationSelector;
        private Duration pollInterval;
        private Duration pollTimeout;

        public Builder sceneRegistry(SceneRegistry sceneRegistry) {
            this.sceneRegistry = sceneRegistry;
            return this;
        }

        public Builder providers(List<AuditOperationProvider> providers) {
            this.providers = providers;
            return this;
        }

        public Builder router(AuditRouter router) {
            this.router = router;
            return this;
        }

        public Builder breakers(CircuitBreakerRegistry breakers) {
            this.breakers = breakers;
            return this;
        }

        public Builder limiters(RateLimiterRegistry limiters) {
            this.limiters = limiters;
            return this;
        }

        public Builder listener(AuditListener listener) {
            this.listener = listener;
            return this;
        }

        public Builder redactors(List<EvidenceRedactor> redactors) {
            this.redactors = redactors;
            return this;
        }

        public Builder taskStore(AuditTaskStore taskStore) {
            this.taskStore = taskStore;
            return this;
        }

        public Builder reviewSink(ReviewResultSink reviewSink) {
            this.reviewSink = reviewSink;
            return this;
        }

        public Builder callbackDispatcher(CallbackDispatcher callbackDispatcher) {
            this.callbackDispatcher = callbackDispatcher;
            return this;
        }

        public Builder arbitrationSelector(AuditArbitrationSelector arbitrationSelector) {
            this.arbitrationSelector = arbitrationSelector;
            return this;
        }

        /** {@link #submitAndWait(AuditRequest)} 默认轮询间隔，null 时回退 2 秒 */
        public Builder pollInterval(Duration pollInterval) {
            this.pollInterval = pollInterval;
            return this;
        }

        /** {@link #submitAndWait(AuditRequest)} 默认等待超时，null 时回退 5 分钟 */
        public Builder pollTimeout(Duration pollTimeout) {
            this.pollTimeout = pollTimeout;
            return this;
        }

        public DefaultContentSafeTemplate build() {
            return new DefaultContentSafeTemplate(sceneRegistry, providers, router, breakers,
                    limiters, listener, redactors, taskStore, reviewSink, callbackDispatcher,
                    arbitrationSelector, pollInterval, pollTimeout);
        }
    }
}
