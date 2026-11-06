package io.github.wrqtec.content.safe.model;

import java.time.Duration;
import java.time.Instant;

import io.github.wrqtec.content.safe.api.util.Asserts;
import io.github.wrqtec.content.safe.model.enums.HealthStatus;

/**
 * 供应商操作健康快照，由后台 {@code ProviderHealthProbe} 异步刷新。不可变
 *
 * @author L.David
 */
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
