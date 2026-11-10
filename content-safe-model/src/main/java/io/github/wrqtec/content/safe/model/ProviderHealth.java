package io.github.wrqtec.content.safe.model;

import java.time.Duration;
import java.time.Instant;

import io.github.wrqtec.content.safe.api.util.Asserts;
import io.github.wrqtec.content.safe.model.enums.HealthStatus;

/**
 * 供应商操作健康快照，由后台 {@code ProviderHealthProbe} 异步刷新。不可变
 *
 * @author L.David
 * @deprecated 配套的 {@code ProviderHealthProbe} 自 1.0.0 发布以来无任何调用方
 *     （core 无健康注册表与探测调度），本记录不会在生产路径上产生实例，请勿使用。
 *     因 1.0.0 已发布无法物理删除（二进制兼容门禁），计划 2.0.0 随探测接口一并移除
 */
@Deprecated(since = "1.1.0", forRemoval = true)
public record ProviderHealth(
        String provider,
        String operation,
        HealthStatus status,
        String message,
        Duration latency,
        Instant checkedAt) {

    public ProviderHealth {
        Asserts.notBlank(provider, "provider");
        Asserts.notNull(status, "status");
        checkedAt = checkedAt == null ? Instant.now() : checkedAt;
    }

    /**
     * 构造"未配置"健康快照
     *
     * @param provider  供应商 key
     * @param operation 操作名
     * @return 状态为 {@link HealthStatus#NOT_CONFIGURED} 的健康快照
     */
    public static ProviderHealth notConfigured(String provider, String operation) {
        return new ProviderHealth(provider, operation, HealthStatus.NOT_CONFIGURED, null, null, null);
    }

    /**
     * 是否可承接流量（UP 或 DEGRADED）
     *
     * @return true 表示可用
     */
    public boolean available() {
        return HealthStatus.UP == status || HealthStatus.DEGRADED == status;
    }
}
