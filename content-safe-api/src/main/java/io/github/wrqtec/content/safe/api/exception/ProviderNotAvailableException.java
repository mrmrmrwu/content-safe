package io.github.wrqtec.content.safe.api.exception;

/**
 * 无可用供应商：未配置、熔断中或健康状态 DOWN
 *
 * @author L.David
 */
public class ProviderNotAvailableException extends ContentSafeException {

    public ProviderNotAvailableException(String message) {
        super(ErrorCodes.PROVIDER_NOT_AVAILABLE, message);
    }

    public ProviderNotAvailableException(String message, Throwable cause) {
        super(ErrorCodes.PROVIDER_NOT_AVAILABLE, message, cause);
    }
}
