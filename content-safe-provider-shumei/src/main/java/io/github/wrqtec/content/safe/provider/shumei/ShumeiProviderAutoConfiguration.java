package io.github.wrqtec.content.safe.provider.shumei;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import io.github.wrqtec.content.safe.api.exception.ConfigInvalidException;
import io.github.wrqtec.content.safe.api.util.Asserts;
import io.github.wrqtec.content.safe.provider.shumei.internal.ShumeiClientInvoker;
import io.github.wrqtec.content.safe.provider.shumei.internal.HttpShumeiClientInvoker;
import io.github.wrqtec.content.safe.spi.CredentialsProvider;
import io.github.wrqtec.content.safe.spi.CredentialsSupport;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * 数美内容安全自动装配：默认不装配，enabled=true 生效（无外部 SDK，装配无类路径条件）；
 * 用户提供的同类型 Bean 可覆盖默认实现（铁律 §5.9）
 *
 * @author L.David
 */
@AutoConfiguration
@ConditionalOnProperty(prefix = ShumeiProviderProperties.PREFIX, name = "enabled", havingValue = "true")
@EnableConfigurationProperties(ShumeiProviderProperties.class)
public class ShumeiProviderAutoConfiguration {

    /** 默认连接超时（毫秒） */
    private static final int DEFAULT_CONNECT_TIMEOUT_MS = 3_000;

    /** 默认读取超时（毫秒；官方同步接口建议超时 1s，取 5s 余量） */
    private static final int DEFAULT_READ_TIMEOUT_MS = 5_000;

    @Bean
    @ConditionalOnMissingBean
    public ShumeiClientInvoker shumeiClientInvoker(ShumeiProviderProperties properties,
                                                   ObjectProvider<List<CredentialsProvider>> credentialsProviders) {
        properties.validate();
        String accessKey = resolveAccessKey(properties, credentialsProviders);
        int connectTimeoutMs = timeoutOf(properties, "connect-timeout-ms", DEFAULT_CONNECT_TIMEOUT_MS);
        int readTimeoutMs = timeoutOf(properties, "read-timeout-ms", DEFAULT_READ_TIMEOUT_MS);
        return new HttpShumeiClientInvoker(accessKey, properties.getAppId(), properties,
                connectTimeoutMs, readTimeoutMs);
    }

    @Bean
    @ConditionalOnMissingBean
    public ShumeiAuditOperationProvider shumeiAuditOperationProvider(
            ShumeiProviderProperties properties, ShumeiClientInvoker client) {
        return new ShumeiAuditOperationProvider(properties, client);
    }

    /**
     * 数美仅 accessKey 单凭证：显式配置 → 自定义 CredentialsProvider → 环境变量 CONTENT_SAFE_SHUMEI_AK
     */
    private String resolveAccessKey(ShumeiProviderProperties properties,
                                    ObjectProvider<List<CredentialsProvider>> credentialsProviders) {
        if (properties.getAccessKey() != null && !properties.getAccessKey().isBlank()) {
            return properties.getAccessKey();
        }
        List<CredentialsProvider> providers = credentialsProviders.getIfAvailable(List::of);
        if (providers != null) {
            for (CredentialsProvider provider : providers) {
                if (ShumeiProviderConstants.PROVIDER.equals(provider.provider())) {
                    CredentialsProvider.Credentials credentials = provider.resolve(configOf(properties));
                    CredentialsProvider.Credentials resolved = Asserts.notNull(credentials,
                            "CredentialsProvider " + provider.getClass().getSimpleName()
                                    + " returned null for shumei");
                    return Asserts.notBlank(resolved.accessKeyId(),
                            "CredentialsProvider resolved blank accessKey for shumei");
                }
            }
        }
        String fromEnv = System.getenv(CredentialsSupport.accessKeyIdEnvKey(ShumeiProviderConstants.PROVIDER));
        if (fromEnv != null && !fromEnv.isBlank()) {
            return fromEnv;
        }
        throw new ConfigInvalidException(
                "shumei accessKey required: set content.safe.providers.shumei.access-key"
                        + " or env CONTENT_SAFE_SHUMEI_AK");
    }

    /** 提供给自定义 CredentialsProvider 的配置视图 */
    private Map<String, Object> configOf(ShumeiProviderProperties properties) {
        Map<String, Object> config = new LinkedHashMap<>();
        if (properties.getAppId() != null && !properties.getAppId().isBlank()) {
            config.put("appId", properties.getAppId());
        }
        config.putAll(properties.getExtra());
        return config;
    }

    private static int timeoutOf(ShumeiProviderProperties properties, String key, int defaultValue) {
        String value = properties.extraValue(key);
        if (value == null) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            throw new ConfigInvalidException(
                    "content.safe.providers.shumei.extra." + key + " must be a number, got: " + value);
        }
    }
}
