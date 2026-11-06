package io.github.wrqtec.content.safe.spi;

import java.util.List;

import io.github.wrqtec.content.safe.model.SceneDefinition;

/**
 * 场景定义注册。业务方通过该接口声明自定义场景；core 与 provider 据此路由
 *
 * @author L.David
 */
public interface SceneDefinitionProvider {

    /**
     * 返回所有已声明场景（含内置）。不可变列表
     *
     * @return 已声明场景列表，不为 null
     */
    List<SceneDefinition> scenes();
}
