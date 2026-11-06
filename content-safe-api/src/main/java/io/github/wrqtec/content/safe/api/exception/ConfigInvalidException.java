package io.github.wrqtec.content.safe.api.exception;

/**
 * 启动期配置非法或无效。
 *
 * @author L.David
 */
public class ConfigInvalidException extends ContentSafeException {

    public ConfigInvalidException(String message) {
        super(ErrorCodes.CONFIG_INVALID, message);
    }

    public ConfigInvalidException(String message, Throwable cause) {
        super(ErrorCodes.CONFIG_INVALID, message, cause);
    }
}
