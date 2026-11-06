package io.github.wrqtec.content.safe.provider.shumei.internal;

import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import io.github.wrqtec.content.safe.api.AuditRequest;
import io.github.wrqtec.content.safe.api.ContentRef;
import io.github.wrqtec.content.safe.api.enums.ContentRefType;
import io.github.wrqtec.content.safe.api.exception.ConfigInvalidException;
import io.github.wrqtec.content.safe.model.SceneDefinition;

/**
 * 数美请求 data 对象装配
 *
 * <p>tokenId 解析链（官方建议：无用户 uid 的场景用唯一数据标识）：
 * {@code context.userId → dataId → 随机 UUID}。
 *
 * @author L.David
 */
public final class ShumeiPayloads {

    /** data 内保留键：场景 ext 撞名视为配置错误，fail-fast */
    private static final java.util.Set<String> RESERVED_KEYS =
            java.util.Set.of("text", "img", "tokenId", "dataId", "ip", "deviceId");

    private ShumeiPayloads() {
    }

    /**
     * 解析 tokenId：context.userId → dataId → 随机 UUID
     */
    public static String tokenIdOf(AuditRequest request) {
        if (request.context() != null && request.context().userId() != null
                && !request.context().userId().isBlank()) {
            return request.context().userId();
        }
        if (request.dataId() != null && !request.dataId().isBlank()) {
            return request.dataId();
        }
        return UUID.randomUUID().toString().replace("-", "");
    }

    /**
     * 文本检测 data 对象
     */
    public static Map<String, Object> textData(String text, String tokenId, String dataId,
                                               String ip, SceneDefinition scene) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("text", text);
        data.put("tokenId", tokenId);
        putIfNotBlank(data, "dataId", dataId);
        putIfNotBlank(data, "ip", ip);
        mergeSceneExt(data, scene);
        return data;
    }

    /**
     * 图片检测 data 对象：URL / base64 / bytes 三通道，bytes 编码为 base64
     */
    public static Map<String, Object> imageData(ContentRef image, String tokenId, String dataId,
                                                String ip, SceneDefinition scene) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("img", imgValueOf(image));
        data.put("tokenId", tokenId);
        putIfNotBlank(data, "dataId", dataId);
        putIfNotBlank(data, "ip", ip);
        mergeSceneExt(data, scene);
        return data;
    }

    private static String imgValueOf(ContentRef image) {
        return switch (image.type()) {
            case URL, BASE64 -> image.value();
            case BYTES -> Base64.getEncoder().encodeToString(image.bytes());
            default -> throw new ConfigInvalidException(
                    "shumei image detect unsupported content ref type: " + image.type());
        };
    }

    /**
     * 场景 ext 合并进 data（辅助检测参数）；与保留键撞名直接抛配置错误
     */
    private static void mergeSceneExt(Map<String, Object> data, SceneDefinition scene) {
        if (scene == null || scene.ext() == null || scene.ext().isEmpty()) {
            return;
        }
        scene.ext().forEach((key, value) -> {
            if (RESERVED_KEYS.contains(key)) {
                throw new ConfigInvalidException(
                        "scene ext key '" + key + "' collides with reserved shumei data field");
            }
            data.put(key, value);
        });
    }

    private static void putIfNotBlank(Map<String, Object> data, String key, String value) {
        if (value != null && !value.isBlank()) {
            data.put(key, value);
        }
    }
}
