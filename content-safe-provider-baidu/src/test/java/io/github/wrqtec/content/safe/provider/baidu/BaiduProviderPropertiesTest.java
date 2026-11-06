package io.github.wrqtec.content.safe.provider.baidu;

import java.util.LinkedHashMap;
import java.util.Map;

import io.github.wrqtec.content.safe.api.exception.ConfigInvalidException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link BaiduProviderProperties} 配置校验测试
 *
 * @author L.David
 */
class BaiduProviderPropertiesTest {

    @Test
    void disabledSkipsValidation() {
        BaiduProviderProperties properties = new BaiduProviderProperties();

        assertThatCode(properties::validate).doesNotThrowAnyException();
    }

    @Test
    void enabledRequiresScenes() {
        BaiduProviderProperties properties = enabled(Map.of());

        assertThatThrownBy(properties::validate)
                .isInstanceOf(ConfigInvalidException.class)
                .hasMessageContaining("scenes");
    }

    @Test
    void enabledAcceptsNumericStrategyId() {
        BaiduProviderProperties properties = enabled(Map.of("comment", "101", "avatar", "202"));

        assertThatCode(properties::validate).doesNotThrowAnyException();
    }

    @Test
    void enabledAcceptsBlankStrategyIdAsDefaultStrategy() {
        BaiduProviderProperties properties = enabled(Map.of("comment", ""));

        assertThatCode(properties::validate).doesNotThrowAnyException();
    }

    @Test
    void rejectsNonNumericStrategyId() {
        BaiduProviderProperties properties = enabled(Map.of("comment", "abc"));

        assertThatThrownBy(properties::validate)
                .isInstanceOf(ConfigInvalidException.class)
                .hasMessageContaining("numeric");
    }

    @Test
    void nullStrategyIdTolerated() {
        Map<String, String> scenes = new LinkedHashMap<>();
        scenes.put("comment", null);
        BaiduProviderProperties properties = enabled(scenes);

        assertThatCode(properties::validate).doesNotThrowAnyException();
    }

    private static BaiduProviderProperties enabled(Map<String, String> scenes) {
        BaiduProviderProperties properties = new BaiduProviderProperties();
        properties.setEnabled(true);
        properties.setScenes(new LinkedHashMap<>(scenes));
        return properties;
    }
}
