package io.github.wrqtec.content.safe.core;

import java.util.List;

import io.github.wrqtec.content.safe.api.constant.Scenes;
import io.github.wrqtec.content.safe.api.enums.ContentType;
import io.github.wrqtec.content.safe.model.SceneDefinition;
import io.github.wrqtec.content.safe.spi.SceneDefinitionProvider;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SceneRegistryTest {

    @Test
    void builtinScenesKeepRegistrationOrder() {
        // 健康端点/覆盖检查按注册序展示场景：保序是观测面契约，不能因 Map.copyOf 丢序
        SceneRegistry registry = new SceneRegistry(null);
        List<String> codes = registry.all().keySet().stream().toList();
        assertEquals(List.of(Scenes.NICKNAME, Scenes.AVATAR, Scenes.COMMENT, Scenes.POST,
                Scenes.PRODUCT_IMAGE, Scenes.IM_MESSAGE, Scenes.SHORT_VIDEO), codes);
    }

    @Test
    void customSceneOverridesBuiltinAndAppendsInOrder() {
        SceneRegistry registry = new SceneRegistry(List.of(
                (SceneDefinitionProvider) () -> List.of(
                        SceneDefinition.of(Scenes.COMMENT, ContentType.IMAGE, null),
                        SceneDefinition.of("biz_forum", ContentType.TEXT, null))));

        assertEquals(ContentType.IMAGE, registry.require(Scenes.COMMENT).type());
        assertEquals(ContentType.TEXT, registry.require("biz_forum").type());
        // 覆盖不改变既有槽位：COMMENT 仍在原位置，新场景追加在尾部
        List<String> codes = registry.all().keySet().stream().toList();
        assertEquals(List.of(Scenes.NICKNAME, Scenes.AVATAR, Scenes.COMMENT, Scenes.POST,
                Scenes.PRODUCT_IMAGE, Scenes.IM_MESSAGE, Scenes.SHORT_VIDEO, "biz_forum"), codes);
    }

    @Test
    void requireUnknownSceneThrows() {
        SceneRegistry registry = new SceneRegistry(null);
        assertThrows(Exception.class, () -> registry.require("no_such_scene"));
    }
}
