package io.github.wrqtec.content.safe.starter.config;

import io.github.wrqtec.content.safe.api.ContentSafeTemplate;
import io.github.wrqtec.content.safe.core.DefaultContentSafeTemplate;
import io.github.wrqtec.content.safe.core.SceneRegistry;
import io.github.wrqtec.content.safe.core.arbitration.FirstNonPassArbitrationPolicy;
import io.github.wrqtec.content.safe.core.callback.CallbackDispatcher;
import io.github.wrqtec.content.safe.core.degrade.CircuitBreakerRegistry;
import io.github.wrqtec.content.safe.core.degrade.SlidingWindowCircuitBreaker;
import io.github.wrqtec.content.safe.core.limiter.RateLimiter;
import io.github.wrqtec.content.safe.core.limiter.RateLimiterRegistry;
import io.github.wrqtec.content.safe.core.listener.CompositeAuditListener;
import io.github.wrqtec.content.safe.core.router.PriorityAuditRouter;
import io.github.wrqtec.content.safe.core.security.DefaultEvidenceRedactor;
import io.github.wrqtec.content.safe.core.task.InMemoryAuditTaskStore;
import io.github.wrqtec.content.safe.spi.AuditArbitrationPolicy;
import io.github.wrqtec.content.safe.spi.AuditListener;
import io.github.wrqtec.content.safe.spi.AuditOperationProvider;
import io.github.wrqtec.content.safe.spi.AuditRouter;
import io.github.wrqtec.content.safe.spi.AuditTaskStore;
import io.github.wrqtec.content.safe.spi.CallbackVerifier;
import io.github.wrqtec.content.safe.spi.CredentialsProvider;
import io.github.wrqtec.content.safe.spi.EvidenceRedactor;
import io.github.wrqtec.content.safe.spi.ReviewResultSink;
import io.github.wrqtec.content.safe.spi.SceneDefinitionProvider;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.core.io.ClassPathResource;

/**
 * ContentSafe 自动装配，仅做条件装配与配置绑定，不含任何审核编排逻辑；
 * 所有对外 Bean 均 {@link ConditionalOnMissingBean}，业务方可整体替换
 *
 * @author L.David
 */
@AutoConfiguration
@ConditionalOnClass(ContentSafeTemplate.class)
@EnableConfigurationProperties(ContentSafeProperties.class)
public class ContentSafeAutoConfiguration {

    /** 启动期配置契约校验（纯内存检查）：schema-version 等不支持即 fail-fast */
    @Bean
    @ConditionalOnMissingBean
    public ContentSafeConfigValidator contentSafeConfigValidator(ContentSafeProperties properties) {
        return new ContentSafeConfigValidator(properties);
    }

    /** 自动导入 classpath:content-safe/scenes.json（不存在则忽略） */
    @Bean
    @ConditionalOnMissingBean(JsonSceneDefinitionProvider.class)
    public JsonSceneDefinitionProvider jsonSceneDefinitionProvider() {
        ClassPathResource resource = new ClassPathResource(JsonSceneDefinitionProvider.DEFAULT_SCENES_CLASSPATH);
        return new JsonSceneDefinitionProvider(resource);
    }

    @Bean
    @ConditionalOnMissingBean
    public SceneRegistry sceneRegistry(ObjectProvider<SceneDefinitionProvider> providers) {
        return new SceneRegistry(providers.orderedStream().toList());
    }

    @Bean
    @ConditionalOnMissingBean
    public AuditRouter auditRouter() {
        return new PriorityAuditRouter();
    }

    @Bean
    @ConditionalOnMissingBean
    public FirstNonPassArbitrationPolicy firstNonPassArbitrationPolicy() {
        return new FirstNonPassArbitrationPolicy();
    }

    @Bean
    @ConditionalOnMissingBean
    public ArbitrationPolicyRegistry arbitrationPolicyRegistry(ObjectProvider<AuditArbitrationPolicy> policies,
                                                               ContentSafeProperties properties) {
        return new ArbitrationPolicyRegistry(policies.orderedStream().toList(), properties);
    }

    @Bean
    @ConditionalOnMissingBean
    public CircuitBreakerRegistry circuitBreakerRegistry(ContentSafeProperties properties) {
        ContentSafeProperties.CircuitBreakerProperties cb = properties.getDefaults().getCircuitBreaker();
        return new CircuitBreakerRegistry(() -> new SlidingWindowCircuitBreaker(
                cb.getWindowSize(), cb.getFailureRateThreshold(), cb.getMinCalls(),
                cb.getOpenDuration(), cb.getHalfOpenMaxCalls()));
    }

    /**
     * 限流器注册表：生效额度 = overrides[planKey] > 全局 qps/maxConcurrency > plan 声明；
     * enabled=false 时全部放行
     */
    @Bean
    @ConditionalOnMissingBean
    public RateLimiterRegistry rateLimiterRegistry(ContentSafeProperties properties) {
        ContentSafeProperties.RateLimiterProperties rl = properties.getDefaults().getRateLimiter();
        if (!rl.isEnabled()) {
            return new RateLimiterRegistry((planKey, limits) -> RateLimiter.unlimited());
        }
        return new RateLimiterRegistry((planKey, limits) -> {
            ContentSafeProperties.Limit override = rl.getOverrides().get(planKey);
            Double qps = firstNonNull(override == null ? null : override.getQps(), rl.getQps(),
                    limits == null ? null : limits.qps());
            Integer maxConcurrency = firstNonNull(override == null ? null : override.getMaxConcurrency(),
                    rl.getMaxConcurrency(), limits == null ? null : limits.maxConcurrency());
            return RateLimiterRegistry.limiterOf(qps, maxConcurrency);
        });
    }

    @SafeVarargs
    private static <T> T firstNonNull(T... candidates) {
        for (T candidate : candidates) {
            if (candidate != null) {
                return candidate;
            }
        }
        return null;
    }

    @Bean
    @ConditionalOnMissingBean
    public AuditTaskStore auditTaskStore() {
        return new InMemoryAuditTaskStore();
    }

    @Bean
    @ConditionalOnMissingBean
    public EvidenceRedactor defaultEvidenceRedactor() {
        return new DefaultEvidenceRedactor();
    }

    /** 凭据解析链：自定义 CredentialsProvider 优先，缺省回退静态配置与环境变量 */
    @Bean
    @ConditionalOnMissingBean
    public CredentialsResolver credentialsResolver(ObjectProvider<CredentialsProvider> providers,
                                                   ContentSafeProperties properties) {
        return new CredentialsResolver(providers.orderedStream().toList(), properties);
    }

    /**
     * 组合监听器，懒解析委托列表，避免组合器自身作为 AuditListener Bean 造成的循环依赖
     */
    @Bean
    @ConditionalOnMissingBean
    public CompositeAuditListener compositeAuditListener(ObjectProvider<AuditListener> listeners) {
        return new CompositeAuditListener(() -> listeners.orderedStream()
                .filter(listener -> !(listener instanceof CompositeAuditListener))
                .toList());
    }

    @Bean
    @ConditionalOnBean(CallbackVerifier.class)
    @ConditionalOnMissingBean
    public CallbackDispatcher callbackDispatcher(ObjectProvider<CallbackVerifier> verifiers,
                                                 ObjectProvider<AuditOperationProvider> providers,
                                                 AuditTaskStore taskStore,
                                                 CompositeAuditListener listener,
                                                 ContentSafeProperties properties) {
        return new CallbackDispatcher(verifiers.orderedStream().toList(),
                providers.orderedStream().toList(), taskStore, listener, properties.getCallbackSecret());
    }

    @Bean
    @ConditionalOnMissingBean
    public ContentSafeTemplate contentSafeTemplate(SceneRegistry sceneRegistry,
                                                   ObjectProvider<AuditOperationProvider> providers,
                                                   AuditRouter router,
                                                   CircuitBreakerRegistry breakers,
                                                   RateLimiterRegistry limiters,
                                                   CompositeAuditListener listener,
                                                   ObjectProvider<EvidenceRedactor> redactors,
                                                   AuditTaskStore taskStore,
                                                   ObjectProvider<ReviewResultSink> reviewSinks,
                                                   ObjectProvider<CallbackDispatcher> callbackDispatcher) {
        return new DefaultContentSafeTemplate(sceneRegistry, providers.orderedStream().toList(), router,
                breakers, limiters, listener, redactors.orderedStream().toList(), taskStore,
                reviewSinks.getIfAvailable(), callbackDispatcher.getIfAvailable());
    }
}
