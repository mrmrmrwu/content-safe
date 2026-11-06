package io.github.wrqtec.content.safe.provider.tencent;

import java.util.LinkedHashMap;
import java.util.Map;

import io.github.wrqtec.content.safe.api.exception.ConfigInvalidException;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 腾讯云内容安全提供者配置
 *
 * <p>图片输入通道：URL / Base64 / bytes 全支持（用户决策 2026-11-06）；
 * AIGC 识别通过 {@code aigcScenes} 独立配置（Type=IMAGE_AIGC），
 * 不与普通审核场景混用 BizType
 *
 * @author L.David
 */
@Data
@ConfigurationProperties(prefix = TencentProviderProperties.PREFIX)
public class TencentProviderProperties {

    /** 配置前缀 */
    public static final String PREFIX = "content.safe.providers.tencent";

    /** 是否启用（默认关闭，显式开启才装配） */
    private boolean enabled;

    /** SecretId（必填，开启时） */
    private String secretId;

    /** SecretKey（必填，开启时） */
    private String secretKey;

    /** 临时凭证 session token（可选） */
    private String sessionToken;

    /** 地域（默认广州；影响 endpoint 推导） */
    private String region = "ap-guangzhou";

    /** 场景 code → TMS/IMS BizType（普通审核） */
    private Map<String, String> scenes = new LinkedHashMap<>();

    /** 场景 code → IMS BizType（AIGC 识别，Type=IMAGE_AIGC，仅 IMAGE 场景生效） */
    private Map<String, String> aigcScenes = new LinkedHashMap<>();

    /** 透传扩展配置（如 gifInterval、gifMaxFrames、connectTimeoutMs、readTimeoutMs） */
    private Map<String, String> extra = new LinkedHashMap<>();

    /**
     * 装配前校验：开启时 secretId/secretKey 必填
     */
    public void validate() {
        if (!enabled) {
            return;
        }
        if (secretId == null || secretId.isBlank() || secretKey == null || secretKey.isBlank()) {
            throw new ConfigInvalidException("tencent provider enabled but secretId/secretKey not configured");
        }
    }
}
