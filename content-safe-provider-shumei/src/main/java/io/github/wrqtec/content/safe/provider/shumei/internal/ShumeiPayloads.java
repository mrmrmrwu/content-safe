package io.github.wrqtec.content.safe.provider.shumei.internal;

import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import io.github.wrqtec.content.safe.api.AuditRequest;
import io.github.wrqtec.content.safe.api.ContentRef;
import io.github.wrqtec.content.safe.api.enums.ContentRefType;
import io.github.wrqtec.content.safe.api.exception.ConfigInvalidException;
import io.github.wrqtec.content.safe.api.exception.UnsupportedContentException;
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

    /** data 内保留键：场景 ext 撞名视为配置错误，fail-fast
     * （url/btId/screenshotType 为视频检测必传字段，ext 撞名会破坏请求语义） */
    private static final Set<String> RESERVED_KEYS =
            Set.of("text", "img", "url", "btId", "screenshotType",
                    "tokenId", "dataId", "ip", "deviceId");

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
            default -> throw new UnsupportedContentException(
                    "shumei image detect unsupported content ref type: " + image.type());
        };
    }

    /**
     * 视频检测 data 对象：仅 URL 通道（视频入参只承诺 URL）。
     * {@code btId}（官方必传，客户侧请求唯一标识）取业务 dataId——它同时是任务关联键，
     * 提交/查询/回调三通道靠它对齐。截帧方式官方必传；detectFrequency/checkFrameCount/
     * returnAllImg/returnAllAudio 等可选截帧参数经场景 ext 透传。
     *
     * @param url 视频 URL（公网可访问）
     * @param tokenId 用户账号标识
     * @param btId 客户侧请求唯一标识（任务关联键）
     * @param ip 客户端公网 IP（可空）
     * @param screenshotType 截帧方式（官方必传）
     * @param scene 场景定义（ext 合并进 data，撞保留键 fail-fast）
     */
    public static Map<String, Object> videoData(String url, String tokenId, String btId,
                                                String ip, int screenshotType, SceneDefinition scene) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("url", url);
        data.put("btId", btId);
        data.put("tokenId", tokenId);
        data.put("screenshotType", screenshotType);
        putIfNotBlank(data, "ip", ip);
        mergeSceneExt(data, scene);
        return data;
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
