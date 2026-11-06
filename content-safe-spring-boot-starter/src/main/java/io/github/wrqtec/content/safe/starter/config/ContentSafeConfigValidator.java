package io.github.wrqtec.content.safe.starter.config;

import io.github.wrqtec.content.safe.api.exception.ConfigInvalidException;
import io.github.wrqtec.content.safe.api.util.Asserts;

/**
 * 启动期配置契约校验：不支持的全局契约值直接 fail-fast 抛
 * {@link ConfigInvalidException}（纯内存检查，无远程探测），
 * 由 {@code ContentSafeFailureAnalyzer} 转成可读的启动失败报告
 *
 * @author L.David
 */
public class ContentSafeConfigValidator {

    /** 当前支持的 content.safe.schema-version */
    public static final int SUPPORTED_SCHEMA_VERSION = 1;

    /** 配置键名（异常消息用，避免散落字面量） */
    private static final String KEY_SCHEMA_VERSION = "content.safe.schema-version";

    private final ContentSafeProperties properties;

    public ContentSafeConfigValidator(ContentSafeProperties properties) {
        this.properties = Asserts.notNull(properties, "properties");
        validate();
    }

    private void validate() {
        if (properties.getSchemaVersion() != SUPPORTED_SCHEMA_VERSION) {
            throw new ConfigInvalidException("unsupported " + KEY_SCHEMA_VERSION + ": "
                    + properties.getSchemaVersion() + " (supported: " + SUPPORTED_SCHEMA_VERSION + ")");
        }
    }
}
