package io.github.wrqtec.content.safe.starter.actuator;

import io.github.wrqtec.content.safe.core.SceneRegistry;
import io.github.wrqtec.content.safe.core.degrade.CircuitBreakerRegistry;
import io.github.wrqtec.content.safe.spi.AuditOperationProvider;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Actuator/Micrometer 可选装配：仅当对应类与 Bean 存在时生效。
 * 类型级 @ConditionalOnClass 保证缺少 actuator/micrometer 依赖时不加载实现类。
 *
 * @author L.David
 */
@AutoConfiguration(afterName = "io.github.wrqtec.content.safe.starter.config.ContentSafeAutoConfiguration")
public class ContentSafeActuatorAutoConfiguration {

    /**
     * 健康指示器装配，仅当 actuator 健康检查类存在时生效。
     *
     * @author L.David
     */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(HealthIndicator.class)
    static class HealthConfiguration {

        @Bean
        @ConditionalOnMissingBean
        ContentSafeHealthIndicator contentSafeHealthIndicator(SceneRegistry sceneRegistry,
                                                              ObjectProvider<AuditOperationProvider> providers,
                                                              CircuitBreakerRegistry breakers) {
            return new ContentSafeHealthIndicator(sceneRegistry, providers.orderedStream().toList(), breakers);
        }
    }

    /**
     * Micrometer 指标装配，仅当 MeterRegistry 可用时生效。
     *
     * @author L.David
     */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(MeterRegistry.class)
    static class MetricsConfiguration {

        @Bean
        @ConditionalOnBean(MeterRegistry.class)
        @ConditionalOnMissingBean
        ContentSafeMetrics contentSafeMetrics(MeterRegistry registry) {
            return new ContentSafeMetrics(registry);
        }
    }
}
