package io.github.wrqtec.content.safe.provider.aliyun;

import java.util.HashMap;
import java.util.Map;

import io.github.wrqtec.content.safe.api.exception.ConfigInvalidException;
import io.github.wrqtec.content.safe.spi.AuditOperationProvider;
import io.github.wrqtec.content.safe.spi.CallbackVerifier;
import io.github.wrqtec.content.safe.spi.CredentialsProvider;
import io.github.wrqtec.content.safe.spi.CredentialsProvider.Credentials;
import io.github.wrqtec.content.safe.spi.CredentialsSupport;
import io.github.wrqtec.content.safe.provider.aliyun.internal.AliyunCallbackVerifier;
import io.github.wrqtec.content.safe.provider.aliyun.internal.GreenClientInvoker;
import io.github.wrqtec.content.safe.provider.aliyun.internal.TeaGreenClientInvoker;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * 阿里云内容安全自动装配。仅当 classpath 存在 green 2.0 SDK 且
 * {@code content.safe.providers.aliyun.enabled=true} 时生效；
 * 对外 Bean 默认 {@link ConditionalOnMissingBean}，用户可整体覆盖（铁律 §5.9）。
 *
 * <p>凭据解析：优先委托注册了 {@code provider()=="aliyun"} 的 {@link CredentialsProvider}
 * （如 STS 扩展），否则回退静态凭据（配置 ak/sk，缺省查环境变量
 * {@code CONTENT_SAFE_ALIYUN_AK/_SK}）。异常消息绝不携带密钥值（铁律 §9.4）
 *
 * @author L.David
 */
@AutoConfiguration
@ConditionalOnClass(name = "com.aliyun.green20220302.Client")
@ConditionalOnProperty(prefix = AliyunProviderProperties.PREFIX, name = "enabled", havingValue = "true")
@EnableConfigurationProperties(AliyunProviderProperties.class)
public class AliyunProviderAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public GreenClientInvoker aliyunGreenClientInvoker(
            AliyunProviderProperties properties,
            ObjectProvider<CredentialsProvider> credentialsProviders) {
        properties.validate();
        Credentials credentials = resolveCredentials(properties, credentialsProviders);
        return new TeaGreenClientInvoker(credentials, properties);
    }

    @Bean
    @ConditionalOnMissingBean
    public AuditOperationProvider aliyunAuditOperationProvider(
            GreenClientInvoker aliyunGreenClientInvoker,
            AliyunProviderProperties properties) {
        return new AliyunAuditOperationProvider(aliyunGreenClientInvoker, properties);
    }

    @Bean
    @ConditionalOnMissingBean
    public CallbackVerifier aliyunCallbackVerifier(AliyunProviderProperties properties) {
        return new AliyunCallbackVerifier(properties);
    }

    /**
     * 凭据解析：自定义 {@link CredentialsProvider} 优先，静态凭据兜底
     *
     * @throws ConfigInvalidException 凭据缺失时抛出（消息不含任何密钥值）
     */
    private Credentials resolveCredentials(AliyunProviderProperties properties,
                                           ObjectProvider<CredentialsProvider> credentialsProviders) {
        String provider = AliyunProviderConstants.PROVIDER;
        for (CredentialsProvider custom : credentialsProviders) {
            if (provider.equals(custom.provider())) {
                return custom.resolve(configOf(properties));
            }
        }
        return CredentialsSupport
                .resolveStatic(provider, properties.getAk(), properties.getSk(),
                        properties.getSecurityToken(), System::getenv)
                .orElseThrow(() -> new ConfigInvalidException("missing credentials for provider '" + provider
                        + "': configure " + AliyunProviderProperties.PREFIX + ".ak/sk"
                        + " or env " + CredentialsSupport.accessKeyIdEnvKey(provider)
                        + "/" + CredentialsSupport.accessKeySecretEnvKey(provider)));
    }

    /** 将 provider 配置展开为 SPI 约定的 config 子树（供自定义 CredentialsProvider 使用） */
    private Map<String, Object> configOf(AliyunProviderProperties properties) {
        Map<String, Object> config = new HashMap<>();
        putIfNotBlank(config, "ak", properties.getAk());
        putIfNotBlank(config, "sk", properties.getSk());
        putIfNotBlank(config, "securityToken", properties.getSecurityToken());
        putIfNotBlank(config, "endpoint", properties.getEndpoint());
        putIfNotBlank(config, "regionId", properties.getRegionId());
        config.putAll(properties.getExtra());
        return config;
    }

    private static void putIfNotBlank(Map<String, Object> config, String key, String value) {
        if (value != null && !value.isBlank()) {
            config.put(key, value);
        }
    }
}
