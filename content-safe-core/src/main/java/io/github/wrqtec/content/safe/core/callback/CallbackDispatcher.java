package io.github.wrqtec.content.safe.core.callback;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Function;
import java.util.stream.Collectors;

import io.github.wrqtec.content.safe.api.AuditResult;
import io.github.wrqtec.content.safe.api.CallbackRequest;
import io.github.wrqtec.content.safe.api.CallbackResult;
import io.github.wrqtec.content.safe.api.exception.CallbackVerifyException;
import io.github.wrqtec.content.safe.core.limiter.RateLimiter;
import io.github.wrqtec.content.safe.core.limiter.TokenBucketRateLimiter;
import io.github.wrqtec.content.safe.core.listener.AuditEventPublisher;
import io.github.wrqtec.content.safe.core.security.EvidenceRedactors;
import io.github.wrqtec.content.safe.model.SceneDefinition;
import io.github.wrqtec.content.safe.model.enums.TaskStatus;
import io.github.wrqtec.content.safe.model.event.CallbackDeadLetterEvent;
import io.github.wrqtec.content.safe.spi.AuditListener;
import io.github.wrqtec.content.safe.spi.AuditOperation;
import io.github.wrqtec.content.safe.spi.AuditOperationProvider;
import io.github.wrqtec.content.safe.spi.AuditTaskStore;
import io.github.wrqtec.content.safe.spi.CallbackVerifier;
import io.github.wrqtec.content.safe.spi.EvidenceRedactor;
import lombok.extern.slf4j.Slf4j;

/**
 * 回调处理：验签 → 解析 → 脱敏 → 任务终态迁移（幂等）→ 发事件。
 * 终态事件经 {@link AuditEventPublisher} 派生，与 audit()/query() 路径共用同一规则
 * （审核完成必发、风险命中与人审按结果形状派生），保证同一结果无论从哪条链路终态化，
 * 输出事件流一致。
 * 任何失败发布 {@link CallbackDeadLetterEvent} 死信事件并返回 {@code accepted=false}，
 * 不得静默丢弃；回调原文与密钥不进日志。
 * 任务引用查不到统一任务（过期逐出/应用重启/外部提交）时发死信事件保可观测，
 * 但返回受理（accepted=true）——重推无法恢复关联，避免供应商反复重试
 *
 * <p><b>防滥用分层</b>：防重放的第一责任在 {@link CallbackVerifier}（契约要求校验
 * 时间窗/nonce，协议不支持的实现须如实声明并由部署方做网络层补偿）；本类在此之上
 * 提供<b>每 provider 令牌桶限流</b>兜底（构造入参 callbackQps，&le;0 或默认构造不限），
 * 截获重放、验签泄漏、供应商故障重推风暴等场景下限制解析成本。限流拒绝返回
 * {@code accepted=false} 且<b>不发死信事件</b>——洪泛场景下事件本身会被放大成风暴，
 * 可观测性由 WARN 日志承担，非 2xx 应答让供应商按自身策略回退重推
 *
 * @author L.David
 */
@Slf4j
public class CallbackDispatcher {

    /** 回调解析用占位场景名：回调与业务场景无关，仅用于向供应商取操作 */
    private static final String CALLBACK_PROBE_SCENE = "_callback";

    /** 任务引用查不到统一任务时的死信原因 */
    private static final String REASON_TASK_NOT_FOUND =
            "audit task not found for callback reference (evicted, restarted or external submit); result dropped";

    private final Map<String, CallbackVerifier> verifiers;

    private final Map<String, AuditOperationProvider> providers;

    private final AuditTaskStore taskStore;

    private final AuditListener listener;

    /** 结果事件派生器：回调终态与 audit()/query() 路径共用同一派生规则（完成/命中/人审） */
    private final AuditEventPublisher publisher;

    private final List<EvidenceRedactor> redactors;

    private final String callbackSecret;

    /** 每 provider 回调限流（qps<=0 表示不限），懒创建且仅在 verifier 命中后创建 */
    private final double callbackQps;

    private final ConcurrentMap<String, RateLimiter> callbackLimiters = new ConcurrentHashMap<>();

    /**
     * 构造不限流的调度器（1.0.0 兼容形态）
     */
    public CallbackDispatcher(List<CallbackVerifier> verifiers,
                              List<AuditOperationProvider> providers,
                              AuditTaskStore taskStore,
                              AuditListener listener,
                              List<EvidenceRedactor> redactors,
                              String callbackSecret) {
        this(verifiers, providers, taskStore, listener, redactors, callbackSecret, 0D);
    }

    /**
     * @param callbackQps 每 provider 回调处理速率上限（令牌桶），&le;0 表示不限流
     */
    public CallbackDispatcher(List<CallbackVerifier> verifiers,
                              List<AuditOperationProvider> providers,
                              AuditTaskStore taskStore,
                              AuditListener listener,
                              List<EvidenceRedactor> redactors,
                              String callbackSecret,
                              double callbackQps) {
        this.verifiers = indexBy(verifiers, CallbackVerifier::provider);
        this.providers = indexBy(providers, AuditOperationProvider::provider);
        this.taskStore = taskStore;
        this.listener = listener;
        this.publisher = new AuditEventPublisher(listener);
        this.redactors = redactors == null ? List.of() : List.copyOf(redactors);
        this.callbackSecret = callbackSecret;
        this.callbackQps = callbackQps;
    }

    private static <T> Map<String, T> indexBy(List<T> list, Function<T, String> key) {
        if (list == null) {
            return Map.of();
        }
        return list.stream().collect(Collectors.toMap(key, Function.identity(), (a, b) -> a));
    }

    /**
     * 处理回调，验签失败抛 {@link CallbackVerifyException}（并转为死信）
     */
    public CallbackResult dispatch(CallbackRequest request) {
        CallbackVerifier verifier = verifiers.get(request.provider());
        if (verifier == null) {
            return deadLetter(request, "no verifier for provider: " + request.provider());
        }
        // 限流器仅在 verifier 命中后创建：未知 provider 的垃圾请求不进入 limiter 表，
        // 表规模以已注册 provider 数为上界，不构成内存放大点
        RateLimiter limiter = callbackLimiters.computeIfAbsent(request.provider(),
                key -> callbackQps > 0D ? new TokenBucketRateLimiter(callbackQps) : RateLimiter.unlimited());
        if (!limiter.tryAcquire().acquired()) {
            // 限流拒绝不发死信事件：洪泛下事件会被放大成风暴；非 2xx 让供应商回退重推
            log.warn("callback rate limited, provider={}, qps={}", request.provider(), callbackQps);
            return CallbackResult.rejected("callback rate limited for provider: " + request.provider());
        }
        boolean verified;
        try {
            verified = verifier.verify(request, callbackSecret, Map.of());
        } catch (RuntimeException e) {
            deadLetter(request, "verifier error: " + e.getClass().getSimpleName(), e);
            throw new CallbackVerifyException("callback verify error for provider: " + request.provider(), e);
        }
        if (!verified) {
            deadLetter(request, "signature verification failed");
            throw new CallbackVerifyException("callback verify failed for provider: " + request.provider());
        }

        AuditOperation operation = findCallbackOperation(request.provider());
        if (operation == null) {
            return deadLetter(request, "no async-callback operation for provider: " + request.provider());
        }

        CallbackResult parsed;
        try {
            parsed = operation.handleCallback(request);
        } catch (RuntimeException e) {
            return deadLetter(request, "callback parse error: " + e.getClass().getSimpleName(), e);
        }
        if (!parsed.accepted()) {
            return deadLetter(request, "callback rejected by provider: " + parsed.message());
        }
        if (parsed.result() == null) {
            // 受理但无结果：无法终态化（标 SUCCEEDED 会产出 result 为 null 的任务且 query() 返回 null）。
            // 按死信处理且不动任务状态，任务保持 PENDING，供应商重推带结果的回调仍可正常终态迁移
            return deadLetter(request, "callback accepted but carried no audit result");
        }

        // 脱敏先行：任务落库、事件、返回值统一使用脱敏副本（EvidenceRedactor 契约：事件、日志、落库前调用）
        AuditResult result = EvidenceRedactors.redactResult(redactors, parsed.result(), request.provider());
        parsed = CallbackResult.accepted(parsed.taskId(), result);

        // 幂等终态迁移：按供应商任务引用反查统一任务
        taskStore.findByRef(parsed.taskId(), result.dataId())
                .ifPresentOrElse(task -> {
                    boolean done = taskStore.markTerminal(task.taskId(), TaskStatus.SUCCEEDED, result);
                    if (done) {
                        publisher.publish(task.scene(), result);
                    }
                }, () -> {
                    // 任务缺失无法关联场景，结果不可恢复地丢弃：发死信保可观测，返回受理止住供应商重推
                    log.warn("callback dead letter, provider={}, reason={}", request.provider(), REASON_TASK_NOT_FOUND);
                    listener.onEvent(new CallbackDeadLetterEvent(request.provider(), REASON_TASK_NOT_FOUND, null));
                });
        return parsed;
    }

    private AuditOperation findCallbackOperation(String provider) {
        AuditOperationProvider p = providers.get(provider);
        if (p == null) {
            return null;
        }
        // 回调解析与业务场景无关，用占位场景取该供应商任一支持 asyncCallback 的操作。
        // 契约依据：AuditOperationProvider#operations 的 scene 参数仅供能力参考、不得按 scene 过滤
        return p.supportedTypes().stream()
                .map(type -> p.operations(SceneDefinition.of(CALLBACK_PROBE_SCENE, type, null)))
                .flatMap(List::stream)
                .filter(op -> op.capabilities().asyncCallback())
                .findFirst()
                .orElse(null);
    }

    private CallbackResult deadLetter(CallbackRequest request, String reason) {
        return deadLetter(request, reason, null);
    }

    private CallbackResult deadLetter(CallbackRequest request, String reason, Throwable cause) {
        if (cause == null) {
            log.warn("callback dead letter, provider={}, reason={}", request.provider(), reason);
        } else {
            log.warn("callback dead letter, provider={}, reason={}", request.provider(), reason, cause);
        }
        listener.onEvent(new CallbackDeadLetterEvent(request.provider(), reason, null));
        return CallbackResult.rejected(reason);
    }
}
