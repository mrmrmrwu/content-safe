package io.github.wrqtec.content.safe.starter.actuator;

import io.github.wrqtec.content.safe.model.event.AuditEvent;
import io.github.wrqtec.content.safe.model.event.ContentSafeEvent;
import io.github.wrqtec.content.safe.model.event.ProviderDegradedEvent;
import io.github.wrqtec.content.safe.model.event.ProviderThrottledEvent;
import io.github.wrqtec.content.safe.spi.AuditListener;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;

/**
 * Micrometer 审计指标：以 AuditListener 形式接入事件流
 * 注意：tag 只使用低基数字段（scene / suggestion / provider），绝不出现内容与密钥
 *
 * @author L.David
 */
public class ContentSafeMetrics implements AuditListener {

    /** 审核次数计数器名 */
    public static final String METRIC_AUDIT = "content.safe.audit";
    /** 供应商降级计数器名 */
    public static final String METRIC_PROVIDER_DEGRADED = "content.safe.provider.degraded";
    /** 供应商限流计数器名 */
    public static final String METRIC_PROVIDER_THROTTLED = "content.safe.provider.throttled";

    /** 低基数 tag key（契约：绝不出现内容与密钥） */
    private static final String TAG_SCENE = "scene";
    private static final String TAG_SUGGESTION = "suggestion";
    private static final String TAG_OK = "ok";
    private static final String TAG_PROVIDER = "provider";
    private static final String TAG_OPERATION = "operation";

    private final MeterRegistry registry;

    public ContentSafeMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    @Override
    public void onEvent(ContentSafeEvent event) {
        if (event instanceof AuditEvent audit) {
            Counter.builder(METRIC_AUDIT)
                    .tag(TAG_SCENE, String.valueOf(audit.scene()))
                    .tag(TAG_SUGGESTION, String.valueOf(audit.result().suggestion()))
                    .tag(TAG_OK, String.valueOf(audit.result().ok()))
                    .register(registry)
                    .increment();
        } else if (event instanceof ProviderDegradedEvent degraded) {
            Counter.builder(METRIC_PROVIDER_DEGRADED)
                    .tag(TAG_PROVIDER, String.valueOf(degraded.provider()))
                    .tag(TAG_OPERATION, String.valueOf(degraded.operation()))
                    .register(registry)
                    .increment();
        } else if (event instanceof ProviderThrottledEvent throttled) {
            Counter.builder(METRIC_PROVIDER_THROTTLED)
                    .tag(TAG_PROVIDER, String.valueOf(throttled.provider()))
                    .tag(TAG_OPERATION, String.valueOf(throttled.operation()))
                    .register(registry)
                    .increment();
        }
    }
}
