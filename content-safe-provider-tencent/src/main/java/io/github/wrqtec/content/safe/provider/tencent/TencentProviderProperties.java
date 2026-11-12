package io.github.wrqtec.content.safe.provider.tencent;

import java.util.LinkedHashMap;
import java.util.Map;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 腾讯云内容安全提供者配置
 *
 * <p>图片输入通道：URL / Base64 / bytes 全支持（用户决策 2026-11-06）；
 * AIGC 识别双通道：配置期经 {@code aigcScenes} 独立场景（文本 Type=TEXT_AIGC、
 * 图片 Type=IMAGE_AIGC），请求期经 {@code AuditOptions#enableAigcDetect} 开关
 * 在普通场景上切换 Type，两类场景不混用 BizType
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

    /** 场景 code → TMS/IMS BizType（AIGC 识别专用场景：文本 Type=TEXT_AIGC、图片 Type=IMAGE_AIGC） */
    private Map<String, String> aigcScenes = new LinkedHashMap<>();

    /** 透传扩展配置（如 gifInterval、gifMaxFrames、connectTimeoutMs、readTimeoutMs） */
    private Map<String, String> extra = new LinkedHashMap<>();

    /**
     * 装配前校验（仅结构项）。凭证是否存在不在此校验：
     * yml 之外还允许 env（CONTENT_SAFE_TENCENT_*）与自定义 CredentialsProvider，
     * 缺凭证由装配链 resolveCredentials 统一 fail-fast（避免环境变量方式被此处误拒）
     */
    public void validate() {
    }
}
