package io.github.wrqtec.content.safe.api.exception;

/**
 * 回调验签失败，回调应进入死信处理。
 *
 * @author L.David
 */
public class CallbackVerifyException extends ContentSafeException {

    public CallbackVerifyException(String message) {
        super(ErrorCodes.CALLBACK_VERIFY_FAILED, message);
    }

    public CallbackVerifyException(String message, Throwable cause) {
        super(ErrorCodes.CALLBACK_VERIFY_FAILED, message, cause);
    }
}
