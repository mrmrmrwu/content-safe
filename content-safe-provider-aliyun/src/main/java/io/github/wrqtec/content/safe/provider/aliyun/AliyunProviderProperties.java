package io.github.wrqtec.content.safe.provider.aliyun;

import java.util.LinkedHashMap;
import java.util.Map;

import io.github.wrqtec.content.safe.api.exception.ConfigInvalidException;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 阿里云 provider 配置绑定。密钥字段（ak/sk/securityToken）仅用于装配客户端，
 * 任何情况下不得进入日志或异常消息
 *
 * @author L.David
 */
@Data
@ConfigurationProperties(prefix = AliyunProviderProperties.PREFIX)
public class AliyunProviderProperties {

    /** 配置前缀（与 starter 的 content.safe.providers.aliyun 子树同键） */
    public static final String PREFIX = "content.safe.providers.aliyun";

    /** 默认地域 */
    public static final String DEFAULT_REGION_ID = "cn-hangzhou";

    /** endpoint 推导模板：green-cip.<regionId>.aliyuncs.com */
    private static final String ENDPOINT_TEMPLATE = "green-cip.%s.aliyuncs.com";

    /** 是否启用该供应商（默认 false，显式开启才装配） */
    private boolean enabled;

    /** 访问密钥 ID（敏感，禁止进入日志与异常消息） */
    private String ak;

    /** 访问密钥 Secret（敏感，禁止进入日志与异常消息） */
    private String sk;

    /** 临时安全令牌（STS，可选；敏感，禁止进入日志与异常消息） */
    private String securityToken;

    /** 服务接入地址；为空时按 regionId 推导 */
    private String endpoint;

    /** 地域 ID */
    private String regionId = DEFAULT_REGION_ID;

    /** 业务场景 code → 阿里云 serviceCode 映射（如 nickname → nickname_detection） */
    private Map<String, String> scenes = new LinkedHashMap<>();

    /** 供应商级扩展配置（如 connectTimeoutMs / readTimeoutMs） */
    private Map<String, String> extra = new LinkedHashMap<>();

    /**
     * 启动期校验：场景映射非空且映射值非空白，否则 fail-fast
     *
     * @throws ConfigInvalidException 配置非法时抛出（消息不含任何密钥值）
     */
    public void validate() {
        if (scenes.isEmpty()) {
            throw new ConfigInvalidException("aliyun provider enabled but no scene mapping configured: "
                    + "configure " + PREFIX + ".scenes.<sceneCode>=<serviceCode>");
        }
        for (Map.Entry<String, String> entry : scenes.entrySet()) {
            if (entry.getKey() == null || entry.getKey().isBlank()
                    || entry.getValue() == null || entry.getValue().isBlank()) {
                throw new ConfigInvalidException("aliyun scene mapping must not contain blank key/value");
            }
        }
    }

    /**
     * 解析最终接入地址：显式 endpoint 优先，缺省按 regionId 推导
     *
     * @return 接入地址
     */
    public String effectiveEndpoint() {
        if (endpoint != null && !endpoint.isBlank()) {
            return endpoint;
        }
        return String.format(ENDPOINT_TEMPLATE, regionId);
    }
}
