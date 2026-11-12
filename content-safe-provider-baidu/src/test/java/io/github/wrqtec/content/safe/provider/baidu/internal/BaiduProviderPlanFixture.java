package io.github.wrqtec.content.safe.provider.baidu.internal;

import io.github.wrqtec.content.safe.model.OperationLimits;
import io.github.wrqtec.content.safe.model.ProviderPlan;
import io.github.wrqtec.content.safe.model.enums.AuditMode;
import io.github.wrqtec.content.safe.provider.baidu.BaiduAuditOperationProvider;

/**
 * 测试用 plan 工厂
 *
 * @author L.David
 */
final class BaiduProviderPlanFixture {

    private BaiduProviderPlanFixture() {
    }

    static ProviderPlan textPlan(String strategyId) {
        return BaiduAuditOperationProvider.syncPlan("TextCensor", strategyId,
                new OperationLimits(null, null, null, BaiduLimits.maxTextBytes(), 0L, false));
    }

    static ProviderPlan imagePlan(String strategyId) {
        return BaiduAuditOperationProvider.syncPlan("ImageCensor", strategyId,
                new OperationLimits(null, null, null, BaiduLimits.maxImageBytes(), 0L, false));
    }

}
