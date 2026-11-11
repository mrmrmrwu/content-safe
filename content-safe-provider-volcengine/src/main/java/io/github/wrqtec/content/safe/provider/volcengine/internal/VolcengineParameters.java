package io.github.wrqtec.content.safe.provider.volcengine.internal;

import java.util.LinkedHashMap;
import java.util.Map;

import com.google.gson.Gson;
import com.volcengine.model.request.RiskDetectionRequest;
import io.github.wrqtec.content.safe.api.exception.ConfigInvalidException;
import io.github.wrqtec.content.safe.api.util.Asserts;
import io.github.wrqtec.content.safe.model.SceneDefinition;

/**
 * 火山引擎内容安全 envelope 与 parameters 内层 JSON 装配。官方把业务载荷约定为
 * {@code {AppId, Service, Parameters}} 三元组，Parameters 为 JSON 字符串，内层
 * 键名由所配 Service 的控制台文档定义（不进公共抽象，铁律 §5.3）：
 *
 * <ul>
 *   <li>内容写入 {@code extra.parameters-text-key} / {@code extra.parameters-image-key} /
 *       {@code extra.parameters-video-key} 指定的键（文本为原文，图片为 URL 原文或
 *       base64 值，视频为 URL，形态由 Service 决定）</li>
 *   <li>场景级 {@code SceneDefinition.ext()} 其余键值原样透传</li>
 * </ul>
 *
 * @author L.David
 */
public final class VolcengineParameters {

    private static final Gson GSON = new Gson();

    private VolcengineParameters() {
    }

    /**
     * 装配 SDK envelope 请求（AppId + Service + Parameters）
     *
     * @param appId      内容安全控制台申请的应用 ID
     * @param service    内容安全 Service 标识
     * @param contentKey 内容键名（extra 配置）
     * @param content    内容值（文本原文 / URL / base64）
     * @param scene      场景定义（ext 透传，可为 null）
     * @return SDK 请求
     * @throws ConfigInvalidException contentKey 未配置时抛出
     */
    public static RiskDetectionRequest envelope(Integer appId, String service, String contentKey,
                                                String content, SceneDefinition scene) {
        return envelope(appId, service, Map.of(), contentKey, content, scene);
    }

    /**
     * 装配 SDK envelope 请求（AppId + Service + Parameters），parameters 在内容之外
     * 携带额外必传字段（如异步视频的任务关联 dataId）
     *
     * @param appId      内容安全控制台申请的应用 ID
     * @param service    内容安全 Service 标识
     * @param fields     内容之外的必传字段（键值对，覆盖场景 ext 同名键）
     * @param contentKey 内容键名（extra 配置）
     * @param content    内容值（文本原文 / URL / base64）
     * @param scene      场景定义（ext 透传，可为 null）
     * @return SDK 请求
     * @throws ConfigInvalidException contentKey 未配置时抛出
     */
    public static RiskDetectionRequest envelope(Integer appId, String service, Map<String, Object> fields,
                                                String contentKey, String content, SceneDefinition scene) {
        Asserts.notNull(appId, "appId");
        Asserts.notBlank(service, "service");
        RiskDetectionRequest request = new RiskDetectionRequest();
        request.setAppId(appId);
        request.setService(service);
        request.setParameters(toJson(fields, contentKey, content, scene));
        return request;
    }

    /**
     * 装配 parameters JSON
     *
     * @param contentKey 内容键名（extra 配置，缺失即抛出可读配置错误）
     * @param content    内容值（文本原文 / URL / base64）
     * @param scene      场景定义（ext 透传，可为 null）
     * @return parameters JSON 字符串
     * @throws ConfigInvalidException contentKey 未配置时抛出
     */
    public static String toJson(String contentKey, String content, SceneDefinition scene) {
        return toJson(Map.of(), contentKey, content, scene);
    }

    /**
     * 装配 parameters JSON，内容之外携带额外必传字段
     *
     * @param fields     内容之外的必传字段（键值对，覆盖场景 ext 同名键）
     * @param contentKey 内容键名（extra 配置，缺失即抛出可读配置错误）
     * @param content    内容值（文本原文 / URL / base64）
     * @param scene      场景定义（ext 透传，可为 null）
     * @return parameters JSON 字符串
     * @throws ConfigInvalidException contentKey 未配置时抛出
     */
    public static String toJson(Map<String, Object> fields, String contentKey, String content,
                                SceneDefinition scene) {
        if (contentKey == null || contentKey.isBlank()) {
            throw new ConfigInvalidException("volcengine parameters content key not configured: set "
                    + "content.safe.providers.volcengine.extra.parameters-text-key / parameters-image-key "
                    + "to the key defined by your Content Security service documentation");
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        if (scene != null && scene.ext() != null) {
            payload.putAll(scene.ext());
        }
        payload.putAll(fields);
        payload.put(contentKey, content);
        return GSON.toJson(payload);
    }

    /**
     * 从 extra 配置读取内容键名
     *
     * @param extra 供应商 extra 配置
     * @param key  配置键名
     * @return 配置值，未配置时为 null（由 {@link #toJson} 统一报错）
     */
    public static String contentKey(Map<String, String> extra, String key) {
        return extra == null ? null : extra.get(key);
    }
}
