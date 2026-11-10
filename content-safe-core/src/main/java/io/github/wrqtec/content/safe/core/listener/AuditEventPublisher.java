package io.github.wrqtec.content.safe.core.listener;

import io.github.wrqtec.content.safe.api.AuditResult;
import io.github.wrqtec.content.safe.api.enums.RiskSuggestion;
import io.github.wrqtec.content.safe.api.util.Asserts;
import io.github.wrqtec.content.safe.model.event.AuditEvent;
import io.github.wrqtec.content.safe.model.event.ReviewEvent;
import io.github.wrqtec.content.safe.model.event.RiskHitEvent;
import io.github.wrqtec.content.safe.spi.AuditListener;

/**
 * 事件派生器：按结果形状派生审核完成/命中/人审事件并发布。
 * 派生规则集中于此，编排层只调一次 {@link #publish}，新增事件类型只改本类
 *
 * @author L.David
 */
public final class AuditEventPublisher {

    private final AuditListener listener;

    public AuditEventPublisher(AuditListener listener) {
        this.listener = Asserts.notNull(listener, "listener");
    }

    /**
     * 按结果派生事件：审核完成事件必发；命中事件要求非放行、有命中且建议非 ERROR；
     * 建议为 REVIEW 时额外发人审事件
     */
    public void publish(String scene, AuditResult result) {
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
