package io.github.wrqtec.content.safe.provider.baidu;

import java.util.LinkedHashMap;
import java.util.Map;

import io.github.wrqtec.content.safe.api.exception.ConfigInvalidException;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 百度内容审核配置
 *
 * <p>scenes 映射值 = 百度内容审核平台策略中心的 strategyId（数字字符串）；
 * 值为空字符串表示不传 strategyId、走账号默认策略。
 *
 * @author L.David
 */
@Data
@ConfigurationProperties(prefix = BaiduProviderProperties.PREFIX)
public class BaiduProviderProperties {

    public static final String PREFIX = "content.safe.providers.baidu";

    /** 是否启用（缺省 false，未引配置不装配） */
    private boolean enabled;

    /** 百度智能云 API Key；缺省回退环境变量 CONTENT_SAFE_BAIDU_AK */
    private String apiKey;

    /** 百度智能云 Secret Key；缺省回退环境变量 CONTENT_SAFE_BAIDU_SK */
    private String secretKey;

    /** 业务场景 code → 策略中心 strategyId（空字符串 = 默认策略） */
    private Map<String, String> scenes = new LinkedHashMap<>();

    /** 扩展参数：connectTimeoutMs/readTimeoutMs（毫秒）、baseUrl（覆盖默认 API 地址） */
    private Map<String, String> extra = new LinkedHashMap<>();

    /**
     * 启用状态下校验配置合法性，启动 fail-fast
     */
    public void validate() {
        if (!enabled) {
            return;
        }
        if (scenes == null || scenes.isEmpty()) {
            throw new ConfigInvalidException("content.safe.providers.baidu.scenes must not be empty when enabled");
        }
        scenes.forEach((scene, strategyId) -> {
            if (strategyId != null && !strategyId.isBlank() && !strategyId.chars().allMatch(Character::isDigit)) {
                throw new ConfigInvalidException(
                        "content.safe.providers.baidu.scenes." + scene + " must be a numeric strategyId, got: "
                                + abbreviate(strategyId));
            }
        });
    }

    private static String abbreviate(String value) {
        return value.length() <= 16 ? value : value.substring(0, 16) + "...";
    }
}
