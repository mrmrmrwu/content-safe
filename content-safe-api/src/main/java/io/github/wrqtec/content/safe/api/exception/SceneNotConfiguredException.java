package io.github.wrqtec.content.safe.api.exception;

/**
 * 业务场景未注册，或未映射到任何供应商场景
 *
 * @author L.David
 */
public class SceneNotConfiguredException extends ContentSafeException {

    public SceneNotConfiguredException(String message) {
        super(ErrorCodes.SCENE_NOT_CONFIGURED, message);
    }

    public SceneNotConfiguredException(String message, Throwable cause) {
        super(ErrorCodes.SCENE_NOT_CONFIGURED, message, cause);
    }
}
