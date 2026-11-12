package io.github.wrqtec.content.safe.core.listener;

import java.util.List;
import java.util.function.Supplier;

import io.github.wrqtec.content.safe.model.event.ContentSafeEvent;
import io.github.wrqtec.content.safe.spi.AuditListener;
import lombok.extern.slf4j.Slf4j;

/**
 * 组合监听器：逐个派发，单个监听器异常不影响其他监听器，也不影响主流程
 * 支持 {@link Supplier} 懒解析委托列表，避免 Spring 容器中"组合器自身也是 AuditListener"
 * 导致的循环依赖
 *
 * @author L.David
 */
@Slf4j
public class CompositeAuditListener implements AuditListener {

    private final Supplier<List<AuditListener>> delegatesSupplier;

    private volatile List<AuditListener> delegates;

    /**
     * @param delegates 监听器列表，可为 null（视为空）
     */
    public CompositeAuditListener(List<AuditListener> delegates) {
        this(() -> delegates);
    }

    /**
     * 懒解析构造：首次事件到达时才从 {@code delegatesSupplier} 取列表，
     * 结果缓存后不再重复解析
     *
     * @param delegatesSupplier 监听器列表提供者，可为 null（视为空，与 List 构造器一致）
     */
    public CompositeAuditListener(Supplier<List<AuditListener>> delegatesSupplier) {
        this.delegatesSupplier = delegatesSupplier != null ? delegatesSupplier : () -> List.of();
    }

    @Override
    public void onEvent(ContentSafeEvent event) {
        for (AuditListener listener : delegates()) {
            try {
                listener.onEvent(event);
            } catch (RuntimeException e) {
                log.warn("audit listener {} failed on event {}", listener.getClass().getSimpleName(),
                        event.eventType(), e);
            }
        }
    }

    private List<AuditListener> delegates() {
        List<AuditListener> resolved = delegates;
        if (resolved == null) {
            synchronized (this) {
                resolved = delegates;
                if (resolved == null) {
                    List<AuditListener> supplied = delegatesSupplier.get();
                    resolved = supplied == null ? List.of() : List.copyOf(supplied);
                    delegates = resolved;
                }
            }
        }
        return resolved;
    }
}
