package io.github.wrqtec.content.safe.core.callback;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import io.github.wrqtec.content.safe.api.AuditResult;
import io.github.wrqtec.content.safe.api.CallbackRequest;
import io.github.wrqtec.content.safe.api.CallbackResult;
import io.github.wrqtec.content.safe.api.enums.RiskSuggestion;
import io.github.wrqtec.content.safe.api.exception.CallbackVerifyException;
import io.github.wrqtec.content.safe.core.security.EvidenceRedactors;
import io.github.wrqtec.content.safe.model.SceneDefinition;
import io.github.wrqtec.content.safe.model.enums.TaskStatus;
import io.github.wrqtec.content.safe.model.event.AuditEvent;
import io.github.wrqtec.content.safe.model.event.CallbackDeadLetterEvent;
import io.github.wrqtec.content.safe.model.event.ReviewEvent;
import io.github.wrqtec.content.safe.spi.AuditListener;
import io.github.wrqtec.content.safe.spi.AuditOperation;
import io.github.wrqtec.content.safe.spi.AuditOperationProvider;
import io.github.wrqtec.content.safe.spi.AuditTaskStore;
import io.github.wrqtec.content.safe.spi.CallbackVerifier;
import io.github.wrqtec.content.safe.spi.EvidenceRedactor;
import lombok.extern.slf4j.Slf4j;

/**
 * 回调处理：验签 → 解析 → 脱敏 → 任务终态迁移（幂等）→ 发事件
 * 任何失败发布 {@link CallbackDeadLetterEvent} 死信事件并返回 {@code accepted=false}，
 * 不得静默丢弃；回调原文与密钥不进日志。
 * 任务引用查不到统一任务（过期逐出/应用重启/外部提交）时发死信事件保可观测，
 * 但返回受理（accepted=true）——重推无法恢复关联，避免供应商反复重试
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

    private final List<EvidenceRedactor> redactors;

    private final String callbackSecret;

    public CallbackDispatcher(List<CallbackVerifier> verifiers,
                              List<AuditOperationProvider> providers,
                              AuditTaskStore taskStore,
                              AuditListener listener,
                              List<EvidenceRedactor> redactors,
                              String callbackSecret) {
        this.verifiers = indexBy(verifiers, CallbackVerifier::provider);
        this.providers = indexBy(providers, AuditOperationProvider::provider);
        this.taskStore = taskStore;
        this.listener = listener;
        this.redactors = redactors == null ? List.of() : List.copyOf(redactors);
        this.callbackSecret = callbackSecret;
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

        // 脱敏先行：任务落库、事件、返回值统一使用脱敏副本（EvidenceRedactor 契约：事件、日志、落库前调用）
        AuditResult result = EvidenceRedactors.redactResult(redactors, parsed.result(), request.provider());
        parsed = CallbackResult.accepted(parsed.taskId(), result);

        // 幂等终态迁移：按供应商任务引用反查统一任务
        taskStore.findByRef(parsed.taskId(), result == null ? null : result.dataId())
                .ifPresentOrElse(task -> {
                    boolean done = taskStore.markTerminal(task.taskId(), TaskStatus.SUCCEEDED, result);
                    if (done) {
                        listener.onEvent(new AuditEvent(task.scene(), result, null));
                        if (result != null && result.suggestion() == RiskSuggestion.REVIEW) {
                            listener.onEvent(new ReviewEvent(task.scene(), result.dataId(), result, null));
                        }
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
        // 回调解析与业务场景无关，用占位场景取该供应商任一支持 asyncCallback 的操作
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
