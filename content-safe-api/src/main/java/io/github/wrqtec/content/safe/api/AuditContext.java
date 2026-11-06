package io.github.wrqtec.content.safe.api;

import java.util.Map;

import io.github.wrqtec.content.safe.api.util.Asserts;

/**
 * 审核上下文：发起方用户/设备信息，供供应商风控字段与审计使用。不可变。
 *
 * @author L.David
 */
public record AuditContext(String userId, String deviceId, String ip, Map<String, Object> ext) {

    public AuditContext {
        ext = ext == null ? Map.of() : Map.copyOf(ext);
    }

    /**
     * 仅带用户 ID 的上下文快捷构造。
     *
     * @param userId 发起方用户 ID，非空白
     * @return 审核上下文
     */
    public static AuditContext ofUser(String userId) {
        return ofUser(userId, null);
    }

    /**
     * 带用户 ID 与设备 ID 的上下文快捷构造。
     *
     * @param userId   发起方用户 ID，非空白
     * @param deviceId 发起方设备 ID，可为 {@code null}
     * @return 审核上下文
     */
    public static AuditContext ofUser(String userId, String deviceId) {
        return new AuditContext(Asserts.notBlank(userId, "userId"), deviceId, null, null);
    }

    /**
     * 完整上下文快捷构造。
     *
     * @param userId   发起方用户 ID，非空白
     * @param deviceId 发起方设备 ID，可为 {@code null}
     * @param ip       发起方 IP，可为 {@code null}
     * @return 审核上下文
     */
    public static AuditContext of(String userId, String deviceId, String ip) {
        return new AuditContext(Asserts.notBlank(userId, "userId"), deviceId, ip, null);
    }
}
