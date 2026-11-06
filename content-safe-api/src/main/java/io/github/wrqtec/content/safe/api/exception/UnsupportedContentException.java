package io.github.wrqtec.content.safe.api.exception;

/**
 * 内容类型或引用方式超出供应商能力声明（{@code AuditCapabilities.supports()} 未通过）。
 *
 * @author L.David
 */
public class UnsupportedContentException extends ContentSafeException {

    public UnsupportedContentException(String message) {
        super(ErrorCodes.UNSUPPORTED_CONTENT, message);
    }

    public UnsupportedContentException(String message, Throwable cause) {
        super(ErrorCodes.UNSUPPORTED_CONTENT, message, cause);
    }
}
