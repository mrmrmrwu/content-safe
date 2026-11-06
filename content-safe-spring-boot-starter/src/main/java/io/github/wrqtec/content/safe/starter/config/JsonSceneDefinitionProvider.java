package io.github.wrqtec.content.safe.starter.config;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.wrqtec.content.safe.api.enums.ContentType;
import io.github.wrqtec.content.safe.api.enums.Severity;
import io.github.wrqtec.content.safe.api.exception.ConfigInvalidException;
import io.github.wrqtec.content.safe.model.SceneDefinition;
import io.github.wrqtec.content.safe.spi.SceneDefinitionProvider;
import org.springframework.core.io.Resource;

/**
 * 从 classpath:content-safe/scenes.json 加载业务场景定义。
 * 文件不存在视为未配置（返回空列表）；文件存在但非法
 * （JSON 错误、schemaVersion 不支持、type 超出 v1 范围等）在启动期
 * 即抛 {@link ConfigInvalidException}。
 *
 * @author L.David
 */
public class JsonSceneDefinitionProvider implements SceneDefinitionProvider {

    /** 当前支持的 scenes.json schema 版本 */
    public static final int SUPPORTED_SCHEMA_VERSION = 1;

    /** 默认加载的 classpath 场景文件路径 */
    public static final String DEFAULT_SCENES_CLASSPATH = "content-safe/scenes.json";

    /** 场景文件名（用于异常消息，避免散落字面量） */
    private static final String SCENES_FILE = "scenes.json";

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final List<SceneDefinition> scenes;

    public JsonSceneDefinitionProvider(Resource resource) {
        this.scenes = load(resource);
    }

    @Override
    public List<SceneDefinition> scenes() {
        return scenes;
    }

    static List<SceneDefinition> load(Resource resource) {
        if (resource == null || !resource.exists()) {
            return List.of();
        }
        try (InputStream in = resource.getInputStream()) {
            JsonNode root = MAPPER.readTree(in);
            int schemaVersion = root.path("schemaVersion").asInt(-1);
            if (schemaVersion != SUPPORTED_SCHEMA_VERSION) {
                throw new ConfigInvalidException(
                        "unsupported " + SCENES_FILE + " schemaVersion: " + schemaVersion
                                + " (supported: " + SUPPORTED_SCHEMA_VERSION + ")");
            }
            List<SceneDefinition> out = new ArrayList<>();
            for (JsonNode node : root.path("scenes")) {
                out.add(parseScene(node));
            }
            return List.copyOf(out);
        } catch (ConfigInvalidException e) {
            throw e;
        } catch (Exception e) {
            throw new ConfigInvalidException("failed to parse " + SCENES_FILE + ": " + resource.getDescription(), e);
        }
    }

    private static SceneDefinition parseScene(JsonNode node) {
        String code = textOrNull(node, "code");
        String type = textOrNull(node, "type");
        if (code == null || type == null) {
            throw new ConfigInvalidException(SCENES_FILE + " entry requires non-blank 'code' and 'type'");
        }
        ContentType contentType;
        try {
            contentType = ContentType.valueOf(type);
        } catch (IllegalArgumentException e) {
            throw new ConfigInvalidException(SCENES_FILE + " scene '" + code + "' has unsupported type: " + type);
        }
        if (contentType != ContentType.TEXT && contentType != ContentType.IMAGE) {
            throw new ConfigInvalidException(
                    SCENES_FILE + " scene '" + code + "' type " + type + " is not supported in v1 (TEXT/IMAGE only)");
        }
        String severity = textOrNull(node, "defaultSeverity");
        Severity defaultSeverity = null;
        if (severity != null) {
            try {
                defaultSeverity = Severity.valueOf(severity.toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                throw new ConfigInvalidException(
                        SCENES_FILE + " scene '" + code + "' has invalid defaultSeverity: " + severity);
            }
        }
        String description = textOrNull(node, "description");
        Map<String, Object> ext = new LinkedHashMap<>();
        JsonNode extNode = node.path("ext");
        if (extNode.isObject()) {
            for (Iterator<Map.Entry<String, JsonNode>> it = extNode.fields(); it.hasNext(); ) {
                Map.Entry<String, JsonNode> entry = it.next();
                ext.put(entry.getKey(), entry.getValue().asText());
            }
        }
        return new SceneDefinition(code, contentType, description, defaultSeverity, ext);
    }

    private static String textOrNull(JsonNode node, String field) {
        JsonNode value = node.path(field);
        if (value.isMissingNode() || value.isNull()) {
            return null;
        }
        String text = value.asText();
        return text == null || text.isBlank() ? null : text;
    }
}
