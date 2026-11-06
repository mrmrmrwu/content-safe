package io.github.wrqtec.content.safe.api.exception;

/**
 * 请求参数非法。
 *
 * @author L.David
 */
public class InvalidRequestException extends ContentSafeException {

    public InvalidRequestException(String message) {
        super(ErrorCodes.INVALID_REQUEST, message);
    }

    public InvalidRequestException(String message, Throwable cause) {
        super(ErrorCodes.INVALID_REQUEST, message, cause);
    }
}
