package io.github.wrqtec.content.safe.api.exception;

/**
 * 配置了当前版本未实现的特性（如 v1 启用 video）
 *
 * @author L.David
 */
public class UnsupportedFeatureException extends ContentSafeException {

    public UnsupportedFeatureException(String message) {
        super(ErrorCodes.UNSUPPORTED_FEATURE, message);
    }

    public UnsupportedFeatureException(String message, Throwable cause) {
        super(ErrorCodes.UNSUPPORTED_FEATURE, message, cause);
    }
}
