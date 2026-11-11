package io.github.wrqtec.content.safe.provider.shumei.internal;

import io.github.wrqtec.content.safe.model.OperationLimits;
import io.github.wrqtec.content.safe.model.ProviderPlan;
import io.github.wrqtec.content.safe.model.enums.AuditMode;
import io.github.wrqtec.content.safe.provider.shumei.ShumeiAuditOperationProvider;

/**
 * 测试用 plan 工厂
 *
 * @author L.David
 */
final class ShumeiProviderPlanFixture {

    private ShumeiProviderPlanFixture() {
    }

    static ProviderPlan textPlan(String eventId) {
        return ShumeiAuditOperationProvider.syncPlan("TextDetect", eventId,
                new OperationLimits(null, null, ShumeiLimits.DEFAULT_TIMEOUT, 0L, 0L, false));
    }

    static ProviderPlan imagePlan(String eventId) {
        return ShumeiAuditOperationProvider.syncPlan("ImageDetect", eventId,
                new OperationLimits(null, null, ShumeiLimits.DEFAULT_TIMEOUT, 0L, 0L, false));
    }

    static ProviderPlan imageAsyncPlan(String eventId) {
        return ShumeiAuditOperationProvider.asyncPlan(eventId);
    }

    static ProviderPlan videoPlan(String eventId) {
        return ShumeiAuditOperationProvider.videoPlan(eventId);
    }

    static AuditMode syncMode() {
        return AuditMode.SYNC;
    }
}
