package io.github.wrqtec.content.safe.core.router;

import java.util.Comparator;
import java.util.List;

import io.github.wrqtec.content.safe.api.AuditRequest;
import io.github.wrqtec.content.safe.api.exception.ProviderNotAvailableException;
import io.github.wrqtec.content.safe.api.util.Asserts;
import io.github.wrqtec.content.safe.model.enums.ProviderLifecycle;
import io.github.wrqtec.content.safe.spi.AuditOperation;
import io.github.wrqtec.content.safe.spi.AuditRouter;

/**
 * 默认路由：过滤 ACTIVE 生命周期 + 能力匹配，按 {@link AuditOperation#order()} 升序取首个
 * 不感知任何具体供应商
 *
 * @author L.David
 */
public class PriorityAuditRouter implements AuditRouter {

    @Override
    public AuditOperation route(AuditRequest request, List<AuditOperation> candidates) {
        Asserts.notNull(request, "request");
        if (candidates == null || candidates.isEmpty()) {
            throw new ProviderNotAvailableException("no candidate operation for scene: " + request.scene());
        }
        return candidates.stream()
                .filter(op -> op.plan().lifecycle() == ProviderLifecycle.ACTIVE)
                .filter(op -> op.supports(request))
                .min(Comparator.comparingInt(AuditOperation::order))
                .orElseThrow(() -> new ProviderNotAvailableException(
                        "no available operation for scene: " + request.scene()));
    }
}
