package io.github.wrqtec.content.safe.provider.baidu;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import io.github.wrqtec.content.safe.api.exception.ConfigInvalidException;
import io.github.wrqtec.content.safe.api.util.Asserts;
import io.github.wrqtec.content.safe.provider.baidu.internal.BaiduAccessTokenManager;
import io.github.wrqtec.content.safe.provider.baidu.internal.BaiduCensorClient;
import io.github.wrqtec.content.safe.provider.baidu.internal.HttpBaiduCensorClient;
import io.github.wrqtec.content.safe.spi.CredentialsProvider;
import io.github.wrqtec.content.safe.spi.CredentialsSupport;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * 百度内容审核自动装配：默认不装配，enabled=true 生效（无外部 SDK，装配无类路径条件）；
 * 用户提供的同类型 Bean 可覆盖默认实现（铁律 §5.9）
 *
 * @author L.David
 */
@AutoConfiguration
@ConditionalOnProperty(prefix = BaiduProviderProperties.PREFIX, name = "enabled", havingValue = "true")
@EnableConfigurationProperties(BaiduProviderProperties.class)
public class BaiduProviderAutoConfiguration {

    /** 默认连接超时（毫秒） */
    private static final int DEFAULT_CONNECT_TIMEOUT_MS = 3_000;

    /** 默认读取超时（毫秒） */
    private static final int DEFAULT_READ_TIMEOUT_MS = 10_000;

    @Bean
    @ConditionalOnMissingBean
    public BaiduCensorClient baiduCensorClient(BaiduProviderProperties properties,
                                               ObjectProvider<List<CredentialsProvider>> credentialsProviders) {
        properties.validate();
        CredentialsProvider.Credentials credentials = resolveCredentials(properties, credentialsProviders);
        String baseUrl = baseUrlOf(properties);
        int connectTimeoutMs = timeoutOf(properties, "connectTimeoutMs", DEFAULT_CONNECT_TIMEOUT_MS);
        int readTimeoutMs = timeoutOf(properties, "readTimeoutMs", DEFAULT_READ_TIMEOUT_MS);
        BaiduAccessTokenManager tokenManager = new BaiduAccessTokenManager(
                credentials.accessKeyId(), credentials.accessKeySecret(),
                baseUrl, connectTimeoutMs, readTimeoutMs);
        return new HttpBaiduCensorClient(tokenManager, baseUrl, connectTimeoutMs, readTimeoutMs);
    }

    @Bean
    @ConditionalOnMissingBean
    public BaiduAuditOperationProvider baiduAuditOperationProvider(
            BaiduProviderProperties properties, BaiduCensorClient client) {
        return new BaiduAuditOperationProvider(properties, client);
    }

    private CredentialsProvider.Credentials resolveCredentials(
            BaiduProviderProperties properties,
            ObjectProvider<List<CredentialsProvider>> credentialsProviders) {
        List<CredentialsProvider> providers = credentialsProviders.getIfAvailable(List::of);
        if (providers != null) {
            for (CredentialsProvider provider : providers) {
                if (BaiduProviderConstants.PROVIDER.equals(provider.provider())) {
                    CredentialsProvider.Credentials credentials = provider.resolve(configOf(properties));
                    return Asserts.notNull(credentials,
                            "CredentialsProvider " + provider.getClass().getSimpleName()
                                    + " returned null for baidu");
                }
            }
        }
        return CredentialsSupport.resolveStatic(
                        BaiduProviderConstants.PROVIDER,
                        properties.getApiKey(),
                        properties.getSecretKey(),
                        null,
                        System::getenv)
                .orElseThrow(() -> new ConfigInvalidException(
                        "baidu credentials required: set content.safe.providers.baidu.api-key/secret-key"
                                + " or env CONTENT_SAFE_BAIDU_AK / CONTENT_SAFE_BAIDU_SK"));
    }

    /** 提供给自定义 CredentialsProvider 的配置视图 */
    private Map<String, Object> configOf(BaiduProviderProperties properties) {
        Map<String, Object> config = new LinkedHashMap<>();
        putIfNotBlank(config, "apiKey", properties.getApiKey());
        putIfNotBlank(config, "secretKey", properties.getSecretKey());
        config.putAll(properties.getExtra());
        return config;
    }

    private static String baseUrlOf(BaiduProviderProperties properties) {
        String baseUrl = properties.getExtra().get("baseUrl");
        return baseUrl == null || baseUrl.isBlank() ? BaiduProviderConstants.BASE_URL : baseUrl;
    }

    private static int timeoutOf(BaiduProviderProperties properties, String key, int defaultValue) {
        String value = properties.getExtra().get(key);
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            throw new ConfigInvalidException(
                    "content.safe.providers.baidu.extra." + key + " must be a number, got: " + value);
        }
    }

    private static void putIfNotBlank(Map<String, Object> config, String key, String value) {
        if (value != null && !value.isBlank()) {
            config.put(key, value);
        }
    }
}
