package io.github.wrqtec.content.safe.starter.config;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link ContentSafeProperties} 绑定测试：provider 子树的 enabled / scenes 等键绑定正确
 */
class ContentSafePropertiesBindingTest {

    private static ContentSafeProperties bind(Map<String, Object> properties) {
        return new Binder(new MapConfigurationPropertySource(properties))
                .bind(ContentSafeProperties.PREFIX, ContentSafeProperties.class)
                .orElseThrow(() -> new IllegalStateException("bind failed"));
    }

    @Test
    void providerEnabledAndScenesBind() {
        ContentSafeProperties bound = bind(Map.of(
                "content.safe.providers.aliyun.enabled", "true",
                "content.safe.providers.aliyun.ak", "ak-1",
                "content.safe.providers.aliyun.region-id", "cn-hangzhou",
                "content.safe.providers.aliyun.scenes.nickname", "nickname_detection",
                "content.safe.providers.aliyun.scenes.comment", "comment_detection"));

        ContentSafeProperties.ProviderProperties aliyun = bound.getProviders().get("aliyun");
        assertThat(aliyun).isNotNull();
        assertThat(aliyun.isEnabled()).isTrue();
        assertThat(aliyun.getAk()).isEqualTo("ak-1");
        assertThat(aliyun.getRegionId()).isEqualTo("cn-hangzhou");
        assertThat(aliyun.getScenes())
                .containsEntry("nickname", "nickname_detection")
                .containsEntry("comment", "comment_detection");
    }

    @Test
    void providerDefaultsDisabled() {
        ContentSafeProperties bound = bind(Map.of(
                "content.safe.providers.aliyun.ak", "ak-1"));

        assertThat(bound.getProviders().get("aliyun").isEnabled()).isFalse();
        assertThat(bound.getProviders().get("aliyun").getScenes()).isEmpty();
    }
}
