package io.github.wrqtec.content.safe.api.exception;

/**
 * 内容安全统一受控异常基类，必须携带稳定错误码（见 {@link ErrorCodes}）
 *
 * <p>注意：message 中禁止输出 accessKey、secret、签名 URL、用户内容原文</p>
 *
 * @author L.David
 */
public class ContentSafeException extends RuntimeException {

    private final String code;

    public ContentSafeException(String code, String message) {
        super(message);
        this.code = code;
    }

    public ContentSafeException(String code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }

    /**
     * @return 稳定错误码，见 {@link ErrorCodes}
     */
    public String getCode() {
        return code;
    }
}
