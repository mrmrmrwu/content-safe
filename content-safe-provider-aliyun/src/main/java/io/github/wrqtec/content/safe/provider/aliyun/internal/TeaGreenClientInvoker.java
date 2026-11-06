package io.github.wrqtec.content.safe.provider.aliyun.internal;

import java.util.Map;

import com.aliyun.green20220302.Client;
import com.aliyun.green20220302.models.ImageModerationRequest;
import com.aliyun.green20220302.models.ImageModerationResponseBody;
import com.aliyun.green20220302.models.TextModerationRequest;
import com.aliyun.green20220302.models.TextModerationResponseBody;
import com.aliyun.teaopenapi.models.Config;
import com.aliyun.teautil.models.RuntimeOptions;
import io.github.wrqtec.content.safe.api.exception.ConfigInvalidException;
import io.github.wrqtec.content.safe.provider.aliyun.AliyunProviderProperties;
import io.github.wrqtec.content.safe.spi.CredentialsProvider.Credentials;

/**
 * 基于 Tea SDK 的 {@link GreenClientInvoker} 实现。唯一触碰 SDK {@link Client} 的类；
 * 构造期只装配配置与 endpoint，不做任何远程调用（铁律 §5.7 启动轻量）
 *
 * @author L.David
 */
public final class TeaGreenClientInvoker implements GreenClientInvoker {

    /** extra 配置键：连接超时（毫秒） */
    public static final String EXTRA_CONNECT_TIMEOUT_MS = "connect-timeout-ms";

    /** extra 配置键：读取超时（毫秒） */
    public static final String EXTRA_READ_TIMEOUT_MS = "read-timeout-ms";

    /** 默认连接超时（毫秒） */
    private static final int DEFAULT_CONNECT_TIMEOUT_MS = 3000;

    /** 默认读取超时（毫秒） */
    private static final int DEFAULT_READ_TIMEOUT_MS = 5000;

    private final Client client;
    private final RuntimeOptions runtimeOptions;

    public TeaGreenClientInvoker(Credentials credentials, AliyunProviderProperties properties) {
        Config config = new Config()
                .setAccessKeyId(credentials.accessKeyId())
                .setAccessKeySecret(credentials.accessKeySecret())
                .setEndpoint(properties.effectiveEndpoint());
        if (credentials.securityToken() != null && !credentials.securityToken().isBlank()) {
            config.setSecurityToken(credentials.securityToken());
        }
        this.client = newClient(config);
        this.runtimeOptions = new RuntimeOptions()
                .setConnectTimeout(intExtra(properties.getExtra(), EXTRA_CONNECT_TIMEOUT_MS,
                        DEFAULT_CONNECT_TIMEOUT_MS))
                .setReadTimeout(intExtra(properties.getExtra(), EXTRA_READ_TIMEOUT_MS,
                        DEFAULT_READ_TIMEOUT_MS));
    }

    /** 构造 SDK Client；独立成方法便于后续扩展（如代理配置） */
    private static Client newClient(Config config) {
        try {
            return new Client(config);
        } catch (Exception e) {
            throw new ConfigInvalidException("failed to init aliyun green client: " + e.getMessage(), e);
        }
    }

    private static int intExtra(Map<String, String> extra, String key, int defaultValue) {
        String value = extra.get(key);
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            throw new ConfigInvalidException("aliyun extra '" + key + "' must be an integer, got non-numeric value");
        }
    }

    @Override
    public TextModerationResponseBody textModeration(String service, Map<String, String> serviceParameters)
            throws Exception {
        TextModerationRequest request = new TextModerationRequest()
                .setService(service)
                .setServiceParameters(JsonParams.toJson(serviceParameters));
        return client.textModerationWithOptions(request, runtimeOptions).getBody();
    }

    @Override
    public ImageModerationResponseBody imageModeration(String service, Map<String, String> serviceParameters)
            throws Exception {
        ImageModerationRequest request = new ImageModerationRequest()
                .setService(service)
                .setServiceParameters(JsonParams.toJson(serviceParameters));
        return client.imageModerationWithOptions(request, runtimeOptions).getBody();
    }
}
