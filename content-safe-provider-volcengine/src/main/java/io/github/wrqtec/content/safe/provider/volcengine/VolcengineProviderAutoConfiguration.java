package io.github.wrqtec.content.safe.provider.volcengine;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import io.github.wrqtec.content.safe.api.exception.ConfigInvalidException;
import io.github.wrqtec.content.safe.api.util.Asserts;
import io.github.wrqtec.content.safe.provider.volcengine.internal.VolcengineAuditResultNormalizer;
import io.github.wrqtec.content.safe.provider.volcengine.internal.VolcengineClientInvoker;
import io.github.wrqtec.content.safe.provider.volcengine.internal.VolcengineSdkClientInvoker;
import io.github.wrqtec.content.safe.spi.CredentialsProvider;
import io.github.wrqtec.content.safe.spi.CredentialsSupport;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * 火山引擎内容安全自动装配：默认不装配，enabled=true 且 SDK 在类路径上才生效；
 * 用户提供的同类型 Bean 可覆盖默认实现（铁律 §5.9）
 *
 * @author L.David
 */
@AutoConfiguration
@ConditionalOnClass(name = "com.volcengine.service.contentSecurity.impl.ContentSecurityServiceImpl")
@ConditionalOnProperty(prefix = VolcengineProviderProperties.PREFIX, name = "enabled", havingValue = "true")
@EnableConfigurationProperties(VolcengineProviderProperties.class)
public class VolcengineProviderAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public VolcengineClientInvoker volcengineClientInvoker(VolcengineProviderProperties properties,
                                                           ObjectProvider<List<CredentialsProvider>> credentialsProviders) {
        properties.validate();
        return new VolcengineSdkClientInvoker(resolveCredentials(properties, credentialsProviders));
    }

    @Bean
    @ConditionalOnMissingBean
    public VolcengineAuditOperationProvider volcengineAuditOperationProvider(
            VolcengineProviderProperties properties, VolcengineClientInvoker invoker) {
        return new VolcengineAuditOperationProvider(properties, invoker, new VolcengineAuditResultNormalizer());
    }

    private CredentialsProvider.Credentials resolveCredentials(
            VolcengineProviderProperties properties,
            ObjectProvider<List<CredentialsProvider>> credentialsProviders) {
        List<CredentialsProvider> providers = credentialsProviders.getIfAvailable(List::of);
        for (CredentialsProvider provider : providers) {
            if (VolcengineProviderConstants.PROVIDER.equals(provider.provider())) {
                CredentialsProvider.Credentials credentials = provider.resolve(configOf(properties));
                return Asserts.notNull(credentials,
                        "CredentialsProvider " + provider.getClass().getSimpleName()
                                + " returned null for volcengine");
            }
        }
        return CredentialsSupport.resolveStatic(
                        VolcengineProviderConstants.PROVIDER,
                        properties.getAk(),
                        properties.getSk(),
                        properties.getSessionToken(),
                        System::getenv)
                .orElseThrow(() -> new ConfigInvalidException(
                        "missing credentials for provider 'volcengine': set content.safe.providers.volcengine.ak/sk"
                                + " or env CONTENT_SAFE_VOLCENGINE_AK / CONTENT_SAFE_VOLCENGINE_SK"
                                + " / CONTENT_SAFE_VOLCENGINE_STSTOKEN"));
    }

    /** 提供给自定义 CredentialsProvider 的配置视图 */
    private Map<String, Object> configOf(VolcengineProviderProperties properties) {
        Map<String, Object> config = new LinkedHashMap<>();
        putIfNotBlank(config, "ak", properties.getAk());
        putIfNotBlank(config, "sk", properties.getSk());
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
