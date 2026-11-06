package io.github.wrqtec.content.safe.provider.aliyun;

import java.util.LinkedHashMap;
import java.util.Map;

import io.github.wrqtec.content.safe.api.exception.ConfigInvalidException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link AliyunProviderProperties} 单元测试：校验规则与 endpoint 推导
 *
 * @author L.David
 */
class AliyunProviderPropertiesTest {

    @Test
    void defaultEndpointDerivedFromRegion() {
        AliyunProviderProperties properties = new AliyunProviderProperties();
        assertThat(properties.effectiveEndpoint()).isEqualTo("green-cip.cn-hangzhou.aliyuncs.com");
        properties.setRegionId("cn-shanghai");
        assertThat(properties.effectiveEndpoint()).isEqualTo("green-cip.cn-shanghai.aliyuncs.com");
    }

    @Test
    void explicitEndpointWins() {
        AliyunProviderProperties properties = new AliyunProviderProperties();
        properties.setEndpoint("green-cip.cn-beijing.aliyuncs.com");
        assertThat(properties.effectiveEndpoint()).isEqualTo("green-cip.cn-beijing.aliyuncs.com");
    }

    @Test
    void emptyScenesRejected() {
        AliyunProviderProperties properties = new AliyunProviderProperties();
        assertThatThrownBy(properties::validate)
                .isInstanceOf(ConfigInvalidException.class)
                .hasMessageContaining("scenes");
    }

    @Test
    void blankSceneKeyOrValueRejected() {
        AliyunProviderProperties properties = new AliyunProviderProperties();
        Map<String, String> scenes = new LinkedHashMap<>();
        scenes.put("comment", "  ");
        properties.setScenes(scenes);
        assertThatThrownBy(properties::validate)
                .isInstanceOf(ConfigInvalidException.class);
    }

    @Test
    void validScenesPass() {
        AliyunProviderProperties properties = new AliyunProviderProperties();
        properties.setScenes(Map.of("comment", "comment_detection"));
        properties.validate();
    }
}
