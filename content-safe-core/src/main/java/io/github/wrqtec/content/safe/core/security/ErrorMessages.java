package io.github.wrqtec.content.safe.core.security;

import java.util.regex.Pattern;

/**
 * 失败消息净化：provider 异常 message 会被聚合进 {@link io.github.wrqtec.content.safe.api.exception.AllProvidersFailedException}
 * 暴露给调用方，第三方 SDK 的异常消息可能夹带签名 URL、访问令牌或用户内容片段，统一在此兜底处理
 *
 * @author L.David
 */
public final class ErrorMessages {

    /** 疑似令牌/签名片段：长度 ≥16 的无分隔 base64/hex 形态串 */
    private static final Pattern SENSITIVE_TOKEN = Pattern.compile("[A-Za-z0-9+/=_-]{16,}");

    private static final int MAX_LENGTH = 120;

    private ErrorMessages() {
    }

    /** 长度 ≥16 的疑似令牌/签名片段替换为 ***，整体限长 {@value #MAX_LENGTH} 字符 */
    public static String sanitize(String message) {
        if (message == null || message.isBlank()) {
            return "";
        }
        String sanitized = SENSITIVE_TOKEN.matcher(message).replaceAll("***");
        return sanitized.length() > MAX_LENGTH ? sanitized.substring(0, MAX_LENGTH) : sanitized;
    }
}
