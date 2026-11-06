package io.github.wrqtec.content.safe.api.exception;

/**
 * 路由链上全部供应商调用失败
 *
 * @author L.David
 */
public class AllProvidersFailedException extends ContentSafeException {

    public AllProvidersFailedException(String message) {
        super(ErrorCodes.ALL_PROVIDER_FAILED, message);
    }

    public AllProvidersFailedException(String message, Throwable cause) {
        super(ErrorCodes.ALL_PROVIDER_FAILED, message, cause);
    }
}
