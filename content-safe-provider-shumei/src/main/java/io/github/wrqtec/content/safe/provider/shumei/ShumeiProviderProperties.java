package io.github.wrqtec.content.safe.provider.shumei;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

import io.github.wrqtec.content.safe.api.exception.ConfigInvalidException;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 数美内容安全配置
 *
 * <p>scenes 映射值 = 与数美约定的 eventId（事件标识，官方强约定，必传）；
 * 数美无场景级策略 ID，检测类型由 {@code extra} 的 text-type/image-type 控制。
 *
 * @author L.David
 */
@Data
@ConfigurationProperties(prefix = ShumeiProviderProperties.PREFIX)
public class ShumeiProviderProperties {

    public static final String PREFIX = "content.safe.providers.shumei";

    /** 合法集群标识（北京 bj / 上海 sh / 硅谷 gg / 弗吉尼亚 fjny / 新加坡 xjp，官方请求地址表） */
    private static final Pattern REGION_PATTERN = Pattern.compile("[a-z0-9-]+");

    /** 是否启用（缺省 false，未引配置不装配） */
    private boolean enabled;

    /** 数美接口认证密钥（accessKey）；缺省回退环境变量 CONTENT_SAFE_SHUMEI_AK */
    private String accessKey;

    /** 应用标识（官方强校验，需与数美约定开通） */
    private String appId;

    /** 集群标识：文本 bj/sh/fjny/xjp，图片 bj/sh/gg/xjp，默认 sh */
    private String region = ShumeiProviderConstants.DEFAULT_REGION;

    /** 业务场景 code → 数美 eventId（事件标识，非空） */
    private Map<String, String> scenes = new LinkedHashMap<>();

    /** 扩展参数：connect-timeout-ms/read-timeout-ms（毫秒）、text-type/image-type（风险类型）、
     * text-base-url/image-base-url（覆盖默认集群地址） */
    private Map<String, String> extra = new LinkedHashMap<>();

    /**
     * 启用状态下校验配置合法性，启动 fail-fast
     */
    public void validate() {
        if (!enabled) {
            return;
        }
        if (appId == null || appId.isBlank()) {
            throw new ConfigInvalidException(
                    "content.safe.providers.shumei.app-id must not be blank when enabled");
        }
        if (region == null || !REGION_PATTERN.matcher(region).matches()) {
            throw new ConfigInvalidException(
                    "content.safe.providers.shumei.region must match [a-z0-9-]+, got: " + region);
        }
        if (scenes == null || scenes.isEmpty()) {
            throw new ConfigInvalidException(
                    "content.safe.providers.shumei.scenes must not be empty when enabled");
        }
        scenes.forEach((scene, eventId) -> {
            if (eventId == null || eventId.isBlank()) {
                throw new ConfigInvalidException(
                        "content.safe.providers.shumei.scenes." + scene
                                + " must be a non-blank eventId agreed with shumei");
            }
        });
    }

    /**
     * extra 取值（空白视为未配置）
     *
     * @param key extra 键
     * @return 配置值，未配置返回 {@code null}
     */
    public String extraValue(String key) {
        String value = extra == null ? null : extra.get(key);
        return value == null || value.isBlank() ? null : value;
    }
}
