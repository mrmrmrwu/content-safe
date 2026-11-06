package io.github.wrqtec.content.safe.provider.shumei;

import java.util.LinkedHashMap;
import java.util.Map;

import io.github.wrqtec.content.safe.api.exception.ConfigInvalidException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link ShumeiProviderProperties} 配置校验测试
 *
 * @author L.David
 */
class ShumeiProviderPropertiesTest {

    @Test
    void disabledSkipsValidation() {
        ShumeiProviderProperties properties = new ShumeiProviderProperties();

        assertThatCode(properties::validate).doesNotThrowAnyException();
    }

    @Test
    void enabledRequiresAppId() {
        ShumeiProviderProperties properties = new ShumeiProviderProperties();
        properties.setEnabled(true);
        properties.setScenes(new LinkedHashMap<>(Map.of("comment", "text")));

        assertThatThrownBy(properties::validate)
                .isInstanceOf(ConfigInvalidException.class)
                .hasMessageContaining("app-id");
    }

    @Test
    void enabledRequiresScenes() {
        ShumeiProviderProperties properties = new ShumeiProviderProperties();
        properties.setEnabled(true);
        properties.setAppId("default");

        assertThatThrownBy(properties::validate)
                .isInstanceOf(ConfigInvalidException.class)
                .hasMessageContaining("scenes");
    }

    @Test
    void enabledAcceptsValidConfiguration() {
        ShumeiProviderProperties properties = enabled(Map.of("comment", "text", "avatar", "image"));

        assertThatCode(properties::validate).doesNotThrowAnyException();
        assertThat(properties.getRegion()).isEqualTo("sh");
    }

    @Test
    void rejectsBlankEventId() {
        ShumeiProviderProperties properties = enabled(Map.of("comment", " "));

        assertThatThrownBy(properties::validate)
                .isInstanceOf(ConfigInvalidException.class)
                .hasMessageContaining("eventId");
    }

    @Test
    void rejectsNullEventId() {
        Map<String, String> scenes = new LinkedHashMap<>();
        scenes.put("comment", null);
        ShumeiProviderProperties properties = enabled(scenes);

        assertThatThrownBy(properties::validate)
                .isInstanceOf(ConfigInvalidException.class)
                .hasMessageContaining("eventId");
    }

    @Test
    void rejectsIllegalRegion() {
        ShumeiProviderProperties properties = enabled(Map.of("comment", "text"));
        properties.setRegion("SH_1!");

        assertThatThrownBy(properties::validate)
                .isInstanceOf(ConfigInvalidException.class)
                .hasMessageContaining("region");
    }

    @Test
    void acceptsArbitraryRegionToken() {
        ShumeiProviderProperties properties = enabled(Map.of("comment", "text"));
        properties.setRegion("fjny");

        assertThatCode(properties::validate).doesNotThrowAnyException();
    }

    @Test
    void extraValueTreatsBlankAsAbsent() {
        ShumeiProviderProperties properties = new ShumeiProviderProperties();
        properties.setExtra(new LinkedHashMap<>(Map.of("text-type", " ", "image-type", "POLITY")));

        assertThat(properties.extraValue("text-type")).isNull();
        assertThat(properties.extraValue("image-type")).isEqualTo("POLITY");
        assertThat(properties.extraValue("absent")).isNull();
    }

    private static ShumeiProviderProperties enabled(Map<String, String> scenes) {
        ShumeiProviderProperties properties = new ShumeiProviderProperties();
        properties.setEnabled(true);
        properties.setAppId("default");
        properties.setScenes(new LinkedHashMap<>(scenes));
        return properties;
    }
}
