package io.github.wrqtec.content.safe.provider.tencent;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import io.github.wrqtec.content.safe.api.exception.ConfigInvalidException;
import io.github.wrqtec.content.safe.api.util.Asserts;
import io.github.wrqtec.content.safe.spi.CallbackVerifier;
import io.github.wrqtec.content.safe.spi.CredentialsProvider;
import io.github.wrqtec.content.safe.spi.CredentialsSupport;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import io.github.wrqtec.content.safe.provider.tencent.internal.TencentAuditResultNormalizer;
import io.github.wrqtec.content.safe.provider.tencent.internal.TencentCallbackVerifier;
import io.github.wrqtec.content.safe.provider.tencent.internal.TencentClientInvoker;
import io.github.wrqtec.content.safe.provider.tencent.internal.TencentSdkClientInvoker;

/**
 * 腾讯云内容安全自动装配：默认不装配，enabled=true 且 SDK 在类路径上才生效；
 * 用户提供的同类型 Bean 可覆盖默认实现（铁律 §5.9）
 *
 * @author L.David
 */
@AutoConfiguration
@ConditionalOnClass(name = "com.tencentcloudapi.tms.v20201229.TmsClient")
@ConditionalOnProperty(prefix = TencentProviderProperties.PREFIX, name = "enabled", havingValue = "true")
@EnableConfigurationProperties(TencentProviderProperties.class)
public class TencentProviderAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public TencentClientInvoker tencentClientInvoker(TencentProviderProperties properties,
                                                     ObjectProvider<List<CredentialsProvider>> credentialsProviders) {
        properties.validate();
        CredentialsProvider.Credentials credentials = resolveCredentials(properties, credentialsProviders);
        return new TencentSdkClientInvoker(credentials, properties);
    }

    @Bean
    @ConditionalOnMissingBean
    public TencentAuditOperationProvider tencentAuditOperationProvider(
            TencentProviderProperties properties, TencentClientInvoker invoker) {
        return new TencentAuditOperationProvider(properties, invoker, new TencentAuditResultNormalizer());
    }

    /**
     * 腾讯回调验签器：官方无签名机制，仅结构校验
     * （防伪依赖回调地址侧 IP 白名单等业务手段）
     */
    @Bean
    @ConditionalOnMissingBean
    public CallbackVerifier tencentCallbackVerifier() {
        return new TencentCallbackVerifier();
    }

    private CredentialsProvider.Credentials resolveCredentials(
            TencentProviderProperties properties,
            ObjectProvider<List<CredentialsProvider>> credentialsProviders) {
        List<CredentialsProvider> providers = credentialsProviders.getIfAvailable(List::of);
        if (providers != null) {
            for (CredentialsProvider provider : providers) {
                if (TencentProviderConstants.PROVIDER.equals(provider.provider())) {
                    CredentialsProvider.Credentials credentials = provider.resolve(configOf(properties));
                    return Asserts.notNull(credentials,
                            "CredentialsProvider " + provider.getClass().getSimpleName()
                                    + " returned null for tencent");
                }
            }
        }
        return CredentialsSupport.resolveStatic(
                        TencentProviderConstants.PROVIDER,
                        properties.getSecretId(),
                        properties.getSecretKey(),
                        properties.getSessionToken(),
                        System::getenv)
                .orElseThrow(() -> new ConfigInvalidException(
                        "missing credentials for provider 'tencent': set content.safe.providers.tencent.secret-id/secret-key"
                                + " or env CONTENT_SAFE_TENCENT_AK / CONTENT_SAFE_TENCENT_SK"
                                + " / CONTENT_SAFE_TENCENT_STSTOKEN"));
    }

    /** 提供给自定义 CredentialsProvider 的配置视图 */
    private Map<String, Object> configOf(TencentProviderProperties properties) {
        Map<String, Object> config = new LinkedHashMap<>();
        putIfNotBlank(config, "secretId", properties.getSecretId());
        putIfNotBlank(config, "secretKey", properties.getSecretKey());
        putIfNotBlank(config, "sessionToken", properties.getSessionToken());
        putIfNotBlank(config, "region", properties.getRegion());
        config.putAll(properties.getExtra());
        return config;
    }

    private static void putIfNotBlank(Map<String, Object> config, String key, String value) {
        if (value != null && !value.isBlank()) {
            config.put(key, value);
        }
    }
}
