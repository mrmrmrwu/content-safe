package io.github.wrqtec.content.safe.model;

import io.github.wrqtec.content.safe.api.util.Asserts;
import io.github.wrqtec.content.safe.model.enums.AuditMode;
import io.github.wrqtec.content.safe.model.enums.ProviderLifecycle;

/**
 * 供应商操作计划：一次可调用的供应商能力单元。不可变。
 *
 * @param provider   供应商 key（yml 配置键，如 {@code aliyun}）
 * @param product    产品线（如阿里 content-detection）
 * @param apiVersion API 版本（供应商接口演进时版本化）
 * @param operation  操作名（如 TextScan / ImageSyncScan）
 * @param service    供应商 service/场景 ID（云厂商侧标识）
 * @param mode       操作模式
 * @param limits     限额声明
 * @param lifecycle  生命周期
 * @author L.David
 */
public record ProviderPlan(
        String provider,
        String product,
        String apiVersion,
        String operation,
        String service,
        AuditMode mode,
        OperationLimits limits,
        ProviderLifecycle lifecycle) {

    public ProviderPlan {
        Asserts.notBlank(provider, "provider");
        Asserts.notBlank(operation, "operation");
        mode = mode == null ? AuditMode.SYNC : mode;
        limits = limits == null ? OperationLimits.unlimited() : limits;
        lifecycle = lifecycle == null ? ProviderLifecycle.ACTIVE : lifecycle;
    }

    /**
     * 路由与熔断隔离键：provider + operation。
     */
    public String planKey() {
        return provider + ":" + operation;
    }
}
