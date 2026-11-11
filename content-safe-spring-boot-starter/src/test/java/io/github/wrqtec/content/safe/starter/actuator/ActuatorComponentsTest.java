package io.github.wrqtec.content.safe.starter.actuator;

import java.time.Duration;
import java.util.List;

import io.github.wrqtec.content.safe.api.AuditResult;
import io.github.wrqtec.content.safe.api.enums.RiskSuggestion;
import io.github.wrqtec.content.safe.core.SceneRegistry;
import io.github.wrqtec.content.safe.core.degrade.CircuitBreakerRegistry;
import io.github.wrqtec.content.safe.core.degrade.SlidingWindowCircuitBreaker;
import io.github.wrqtec.content.safe.core.limiter.RateLimiterRegistry;
import io.github.wrqtec.content.safe.model.event.AuditEvent;
import io.github.wrqtec.content.safe.model.event.ProviderDegradedEvent;
import io.github.wrqtec.content.safe.model.event.ProviderThrottledEvent;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.Status;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ActuatorComponentsTest {

    @Test
    void healthIsUpWithoutOpenBreakers() {
        ContentSafeHealthIndicator indicator = new ContentSafeHealthIndicator(
                new SceneRegistry(List.of()), List.of(), new CircuitBreakerRegistry(),
                new RateLimiterRegistry());
        Health health = indicator.health();
        assertEquals(Status.UP, health.getStatus());
        assertEquals(7, health.getDetails().get("scenes")); // 内置场景 7 个（T-026 起 short_video）
    }

    @Test
    void healthDegradedWhenBreakerOpen() {
        SlidingWindowCircuitBreaker open = new SlidingWindowCircuitBreaker(
                2, 0.5D, 2, Duration.ofMinutes(10), 1);
        open.tryAcquire();
        open.onFailure();
        open.tryAcquire();
        open.onFailure();

        CircuitBreakerRegistry registry = new CircuitBreakerRegistry(() -> open);
        registry.get("fake:op");

        Health health = new ContentSafeHealthIndicator(
                new SceneRegistry(List.of()), List.of(), registry, new RateLimiterRegistry()).health();
        assertEquals(ContentSafeHealthIndicator.STATUS_DEGRADED, health.getStatus().getCode());
        @SuppressWarnings("unchecked")
        List<String> openKeys = (List<String>) health.getDetails().get("openCircuitBreakers");
        assertEquals(List.of("fake:op"), openKeys);
    }

    @Test
    void healthExposesRateLimiterSnapshot() {
        RateLimiterRegistry limiters = new RateLimiterRegistry();
        limiters.get("fake:op", new io.github.wrqtec.content.safe.model.OperationLimits(
                null, 100D, null, 0L, 0L, false));

        Health health = new ContentSafeHealthIndicator(
                new SceneRegistry(List.of()), List.of(), new CircuitBreakerRegistry(), limiters).health();
        @SuppressWarnings("unchecked")
        java.util.Map<String, String> snapshot =
                (java.util.Map<String, String>) health.getDetails().get("rateLimiters");
        assertTrue(snapshot.get("fake:op").contains("token-bucket"));
    }

    @Test
    void healthExposesProviderErrorsInsteadOfSwallowing() {
        io.github.wrqtec.content.safe.spi.AuditOperationProvider broken =
                new io.github.wrqtec.content.safe.spi.AuditOperationProvider() {
                    @Override
                    public String provider() {
                        return "broken";
                    }

                    @Override
                    public java.util.List<io.github.wrqtec.content.safe.api.enums.ContentType> supportedTypes() {
                        return java.util.List.of();
                    }

                    @Override
                    public java.util.List<io.github.wrqtec.content.safe.spi.AuditOperation> operations(
                            io.github.wrqtec.content.safe.model.SceneDefinition scene) {
                        throw new IllegalStateException("accessKey AKTP1234567890123456 rejected");
                    }
                };

        Health health = new ContentSafeHealthIndicator(
                new SceneRegistry(List.of()), List.of(broken), new CircuitBreakerRegistry(),
                new RateLimiterRegistry()).health();
        assertEquals(Status.UP, health.getStatus());
        @SuppressWarnings("unchecked")
        java.util.Map<String, java.util.List<String>> mappings =
                (java.util.Map<String, java.util.List<String>>) health.getDetails().get("sceneMappings");
        assertTrue(mappings.values().stream().allMatch(java.util.List::isEmpty));
        @SuppressWarnings("unchecked")
        java.util.Map<String, String> errors =
                (java.util.Map<String, String>) health.getDetails().get("providerErrors");
        assertTrue(errors.get("broken").contains("***"));
        assertTrue(!errors.get("broken").contains("AKTP1234567890123456"));
    }

    @Test
    void metricsCountAuditAndDegradedEvents() {
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        ContentSafeMetrics metrics = new ContentSafeMetrics(meterRegistry);

        AuditResult result = AuditResult.builder().ok(true).suggestion(RiskSuggestion.REJECT).build();
        metrics.onEvent(new AuditEvent("comment", result, null));
        metrics.onEvent(new AuditEvent("comment", result, null));
        metrics.onEvent(new ProviderDegradedEvent("aliyun", "textScan", "comment", "boom", null));
        metrics.onEvent(new ProviderThrottledEvent("aliyun", "textScan", "comment", "rate limited", null));

        assertEquals(2.0, meterRegistry.get(ContentSafeMetrics.METRIC_AUDIT)
                .tags("scene", "comment", "suggestion", "REJECT").counter().count());
        assertEquals(1.0, meterRegistry.get(ContentSafeMetrics.METRIC_PROVIDER_DEGRADED)
                .tags("provider", "aliyun").counter().count());
        assertEquals(1.0, meterRegistry.get(ContentSafeMetrics.METRIC_PROVIDER_THROTTLED)
                .tags("provider", "aliyun", "operation", "textScan").counter().count());
    }

    @Test
    void metricsTagsNeverContainContent() {
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        ContentSafeMetrics metrics = new ContentSafeMetrics(meterRegistry);
        metrics.onEvent(new AuditEvent("comment",
                AuditResult.builder().ok(false).suggestion(RiskSuggestion.REJECT).build(), null));

        io.micrometer.core.instrument.Meter meter = meterRegistry.get(ContentSafeMetrics.METRIC_AUDIT).meter();
        List<String> tagKeys = meter.getId().getTags().stream()
                .map(io.micrometer.core.instrument.Tag::getKey).toList();
        assertTrue(List.of("scene", "suggestion", "ok").containsAll(tagKeys));
        assertEquals(1.0, meterRegistry.get(ContentSafeMetrics.METRIC_AUDIT)
                .tags("scene", "comment").counter().count());
    }
}
