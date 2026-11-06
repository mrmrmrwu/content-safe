package io.github.wrqtec.content.safe.api.exception;

/**
 * 供应商调用失败：超时、限流、5xx、鉴权失效等
 *
 * @author L.David
 */
public class ProviderCallException extends ContentSafeException {

    public ProviderCallException(String message) {
        super(ErrorCodes.PROVIDER_CALL_ERROR, message);
    }

    public ProviderCallException(String message, Throwable cause) {
        super(ErrorCodes.PROVIDER_CALL_ERROR, message, cause);
    }
}
