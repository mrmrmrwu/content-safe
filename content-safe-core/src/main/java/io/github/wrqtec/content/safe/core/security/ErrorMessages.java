package io.github.wrqtec.content.safe.core.security;

/**
 * @deprecated 净化实现已统一收敛到 {@link io.github.wrqtec.content.safe.api.util.ErrorMessages}
 * （含各 provider 模块），本类仅为兼容保留的直接委托；新代码请直接使用 api 版本。
 *
 * @author L.David
 */
@Deprecated(since = "1.1.0")
public final class ErrorMessages {

    private ErrorMessages() {
    }

    /** 长度 ≥16 的疑似令牌/签名片段替换为 ***，整体限长 120 字符 */
    public static String sanitize(String message) {
        return io.github.wrqtec.content.safe.api.util.ErrorMessages.sanitize(message);
    }
}
