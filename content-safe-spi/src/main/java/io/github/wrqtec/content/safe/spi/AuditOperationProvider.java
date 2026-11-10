package io.github.wrqtec.content.safe.spi;

import java.util.List;

import io.github.wrqtec.content.safe.api.AuditRequest;
import io.github.wrqtec.content.safe.api.enums.ContentType;
import io.github.wrqtec.content.safe.api.util.Asserts;
import io.github.wrqtec.content.safe.model.SceneDefinition;

/**
 * 供应商（如 aliyun / tencent / volcengine）入口。core 通过该接口获取该供应商已配置的操作
 *
 * @author L.David
 */
public interface AuditOperationProvider {

    /**
     * 供应商 key（yml 配置键）
     *
     * @return 供应商 key，如 {@code aliyun}
     */
    String provider();

    /**
     * 该供应商支持的场景类型
     *
     * @return 支持的内容类型列表，不为 null
     */
    List<ContentType> supportedTypes();

    /**
     * 该供应商可用操作列表，必须都是 {@code ACTIVE} 生命周期
     *
     * <p><b>契约：scene 参数仅供能力参考（读取 type/配置联动），不得用于按场景过滤操作</b>。
     * core 回调路由会用<b>占位场景</b>（场景名 {@code _callback}、无默认严重级别）探测
     * 该供应商支持 asyncCallback 的操作；若实现按 scene.code() 查配置过滤，
     * 占位场景探测将静默返回空列表，回调链路直接失效。实现只能依据 scene.type()
     * （以及与类型绑定的供应商侧配置）决定返回哪些操作
     *
     * @param scene 业务场景定义；探测场景下 severity 可能为 null
     * @return 可调用的操作列表（探测场景下为该类型全部回调型操作），不为 null
     */
    List<AuditOperation> operations(SceneDefinition scene);

    /**
     * 是否支持当前请求。默认实现要求 {@code scene} 与 capabilities 匹配
     *
     * @param request 审核请求，不得为 null
     * @param scene   业务场景定义，不得为 null
     * @return true 表示该供应商可处理该请求
     * @throws io.github.wrqtec.content.safe.api.exception.InvalidRequestException 若 request 或 scene 为 null
     */
    default boolean supports(AuditRequest request, SceneDefinition scene) {
        Asserts.notNull(request, "request");
        Asserts.notNull(scene, "scene");
        if (!supportedTypes().contains(scene.type())) {
            return false;
        }
        return operations(scene).stream()
                .anyMatch(op -> op.capabilities().supports(request));
    }
}
