package io.github.wrqtec.content.safe.provider.volcengine;

import java.util.LinkedHashMap;
import java.util.Map;

import io.github.wrqtec.content.safe.api.exception.ConfigInvalidException;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 火山引擎内容安全提供者配置。场景 code 与云厂商侧 Service 标识仅在
 * yml/extra 出现，禁止硬编码进 api/core（铁律 §5.3）
 *
 * @author L.David
 */
@Data
@ConfigurationProperties(prefix = VolcengineProviderProperties.PREFIX)
public class VolcengineProviderProperties {

    /** 配置前缀 */
    public static final String PREFIX = "content.safe.providers.volcengine";

    /** 是否启用（默认关闭，显式开启才装配） */
    private boolean enabled;

    /** 访问密钥 ID（敏感，禁止进入日志与异常消息） */
    private String ak;

    /** 访问密钥 Secret（敏感，禁止进入日志与异常消息） */
    private String sk;

    /** STS 临时会话 token（可选） */
    private String sessionToken;

    /** 签名地域（内容安全产品线固定 cn-north-1） */
    private String region = "cn-north-1";

    /** 场景 code → 内容安全 Service 标识（控制台申请的业务服务名） */
    private Map<String, String> scenes = new LinkedHashMap<>();

    /** 供应商级扩展配置（app-id、parameters-*-key、超时等，透传给客户端） */
    private Map<String, String> extra = new LinkedHashMap<>();

    /**
     * 装配前校验：开启时 app-id（整型）必填；parameters-*-key 由
     * {@code VolcengineAuditOperationProvider} 按场景内容类型校验
     */
    public void validate() {
        if (!enabled) {
            return;
        }
        String appId = extra.get(VolcengineProviderConstants.EXTRA_APP_ID);
        if (appId == null || appId.isBlank()) {
            throw new ConfigInvalidException("volcengine provider enabled but extra '"
                    + VolcengineProviderConstants.EXTRA_APP_ID + "' not configured");
        }
        try {
            Integer.parseInt(appId.trim());
        } catch (NumberFormatException e) {
            throw new ConfigInvalidException("volcengine extra '"
                    + VolcengineProviderConstants.EXTRA_APP_ID + "' must be an integer, got '" + appId + "'");
        }
    }
}
