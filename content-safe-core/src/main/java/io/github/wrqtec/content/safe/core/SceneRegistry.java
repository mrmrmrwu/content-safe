package io.github.wrqtec.content.safe.core;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import io.github.wrqtec.content.safe.api.constant.Scenes;
import io.github.wrqtec.content.safe.api.enums.ContentType;
import io.github.wrqtec.content.safe.api.exception.SceneNotConfiguredException;
import io.github.wrqtec.content.safe.api.util.Asserts;
import io.github.wrqtec.content.safe.model.SceneDefinition;
import io.github.wrqtec.content.safe.spi.SceneDefinitionProvider;

/**
 * 场景注册表：内置场景 + 业务自定义场景（{@link SceneDefinitionProvider}）
 * 业务 code 与云厂商场景 ID 解耦的唯一入口，线程安全（构造后只读）
 *
 * @author L.David
 */
public class SceneRegistry {

    private final Map<String, SceneDefinition> scenes;

    /**
     * @param providers 业务自定义场景提供器，可为 null；自定义场景允许覆盖内置场景
     */
    public SceneRegistry(List<SceneDefinitionProvider> providers) {
        Map<String, SceneDefinition> all = new LinkedHashMap<>();
        // 内置场景
        register(all, SceneDefinition.of(Scenes.NICKNAME, ContentType.TEXT, null));
        register(all, SceneDefinition.of(Scenes.AVATAR, ContentType.IMAGE, null));
        register(all, SceneDefinition.of(Scenes.COMMENT, ContentType.TEXT, null));
        register(all, SceneDefinition.of(Scenes.POST, ContentType.TEXT, null));
        register(all, SceneDefinition.of(Scenes.PRODUCT_IMAGE, ContentType.IMAGE, null));
        register(all, SceneDefinition.of(Scenes.IM_MESSAGE, ContentType.TEXT, null));
        register(all, SceneDefinition.of(Scenes.SHORT_VIDEO, ContentType.VIDEO, null));
        // 业务自定义场景（允许覆盖内置）
        if (providers != null) {
            for (SceneDefinitionProvider provider : providers) {
                List<SceneDefinition> custom = provider.scenes();
                if (custom != null) {
                    custom.forEach(scene -> register(all, scene));
                }
            }
        }
        this.scenes = Map.copyOf(all);
    }

    private static void register(Map<String, SceneDefinition> all, SceneDefinition scene) {
        Asserts.notNull(scene, "scene");
        all.put(scene.code(), scene);
    }

    /**
     * 按场景 code 查找
     *
     * @param code 业务场景 code
     * @return 场景定义，不存在时为空
     */
    public Optional<SceneDefinition> find(String code) {
        return Optional.ofNullable(scenes.get(code));
    }

    /**
     * 获取场景，不存在抛 {@link SceneNotConfiguredException}
     */
    public SceneDefinition require(String code) {
        return find(code).orElseThrow(() ->
                new SceneNotConfiguredException("scene not configured: " + code));
    }

    /**
     * 全部场景（只读视图，构造时快照）
     *
     * @return code → 场景定义
     */
    public Map<String, SceneDefinition> all() {
        return scenes;
    }
}
