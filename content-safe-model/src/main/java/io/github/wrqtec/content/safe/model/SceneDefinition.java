package io.github.wrqtec.content.safe.model;

import java.util.Map;

import io.github.wrqtec.content.safe.api.enums.ContentType;
import io.github.wrqtec.content.safe.api.enums.Severity;
import io.github.wrqtec.content.safe.api.util.Asserts;

/**
 * 业务场景定义。业务 code（如 {@code nickname}）与云厂商场景 ID 解耦，
 * 后者只允许出现在 yml 配置与 provider 内部。不可变。
 *
 * @author L.David
 */
public record SceneDefinition(
        String code,
        ContentType type,
        String description,
        Severity defaultSeverity,
        Map<String, Object> ext) {

    public SceneDefinition {
        Asserts.notBlank(code, "code");
        Asserts.notNull(type, "type");
        ext = ext == null ? Map.of() : Map.copyOf(ext);
    }

    /**
     * 创建场景定义（无描述、无扩展属性）。
     *
     * @param code            业务场景 code
     * @param type            内容类型
     * @param defaultSeverity 默认严重级别
     * @return 场景定义实例
     */
    public static SceneDefinition of(String code, ContentType type, Severity defaultSeverity) {
        return new SceneDefinition(code, type, null, defaultSeverity, null);
    }
}
