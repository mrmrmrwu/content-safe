package io.github.wrqtec.content.safe.starter.config;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import io.github.wrqtec.content.safe.api.exception.ConfigInvalidException;
import io.github.wrqtec.content.safe.api.util.Asserts;
import io.github.wrqtec.content.safe.spi.CredentialsProvider;
import io.github.wrqtec.content.safe.spi.CredentialsProvider.Credentials;
import io.github.wrqtec.content.safe.spi.CredentialsSupport;

/**
 * 凭据解析链：优先委托给注册了对应 {@link CredentialsProvider} 的实现（如 STS/CAM/KMS 扩展），
 * 未注册时回退到静态凭据（content.safe.providers.&lt;name&gt;.ak/sk，缺省再查环境变量
 * CONTENT_SAFE_&lt;PROVIDER&gt;_AK / _SK）
 *
 * <p>安全约束：任何异常消息与日志只出现供应商名，绝不出现密钥值
 *
 * @author L.David
 */
public class CredentialsResolver {

    /** 供应商 config 子树与静态配置中的密钥 key */
    private static final String KEY_AK = "ak";
    private static final String KEY_SK = "sk";
    private static final String KEY_SECURITY_TOKEN = "securityToken";

    private final Map<String, CredentialsProvider> providers = new HashMap<>();
    private final ContentSafeProperties properties;
    private final Function<String, String> envLookup;

    public CredentialsResolver(List<CredentialsProvider> credentialsProviders,
                               ContentSafeProperties properties) {
        this(credentialsProviders, properties, System::getenv);
    }

    /** 可注入 envLookup 便于测试 */
    public CredentialsResolver(List<CredentialsProvider> credentialsProviders,
                               ContentSafeProperties properties,
                               Function<String, String> envLookup) {
        this.properties = Asserts.notNull(properties, "properties");
        this.envLookup = Asserts.notNull(envLookup, "envLookup");
        if (credentialsProviders != null) {
            for (CredentialsProvider provider : credentialsProviders) {
                providers.put(provider.provider(), provider);
            }
        }
    }

    /**
     * 解析指定供应商的访问密钥
     *
     * @param provider 供应商 key（如 aliyun）
     * @throws ConfigInvalidException 凭据缺失时抛出（消息不含任何密钥值）
     */
    public Credentials resolve(String provider) {
        Asserts.notBlank(provider, "provider");
        Map<String, Object> config = configOf(provider);
        CredentialsProvider custom = providers.get(provider);
        if (custom != null) {
            return custom.resolve(config);
        }
        String ak = stringConfig(config, KEY_AK);
        String sk = stringConfig(config, KEY_SK);
        String token = stringConfig(config, KEY_SECURITY_TOKEN);
        return CredentialsSupport.resolveStatic(provider, ak, sk, token, envLookup)
                .orElseThrow(() -> new ConfigInvalidException("missing credentials for provider '" + provider
                        + "': configure " + ContentSafeProperties.PREFIX + ".providers." + provider + ".ak/sk"
                        + " or env " + CredentialsSupport.accessKeyIdEnvKey(provider)
                        + "/" + CredentialsSupport.accessKeySecretEnvKey(provider)));
    }

    /** 将供应商配置展开为 SPI 约定的 config 子树 */
    public Map<String, Object> configOf(String provider) {
        ContentSafeProperties.ProviderProperties props = properties.getProviders().get(provider);
        Map<String, Object> config = new HashMap<>();
        if (props != null) {
            putIfNotBlank(config, KEY_AK, props.getAk());
            putIfNotBlank(config, KEY_SK, props.getSk());
            putIfNotBlank(config, "endpoint", props.getEndpoint());
            putIfNotBlank(config, "regionId", props.getRegionId());
            config.putAll(props.getExtra());
        }
        return config;
    }

    private static void putIfNotBlank(Map<String, Object> config, String key, String value) {
        if (!isBlank(value)) {
            config.put(key, value);
        }
    }

    private static String stringConfig(Map<String, Object> config, String key) {
        Object value = config.get(key);
        return value instanceof String s ? s : null;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
