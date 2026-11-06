package io.github.wrqtec.content.safe.model;

import java.util.Map;

import io.github.wrqtec.content.safe.api.enums.ContentType;
import io.github.wrqtec.content.safe.api.enums.Severity;
import io.github.wrqtec.content.safe.api.exception.InvalidRequestException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * {@link SceneDefinition} 单元测试
 *
 * @author L.David
 */
class SceneDefinitionTest {

    @Test
    void extIsImmutable() {
        SceneDefinition scene = new SceneDefinition("nickname", ContentType.TEXT, "desc",
                Severity.MEDIUM, Map.of("k", "v"));
        assertEquals("v", scene.ext().get("k"));
        assertThrows(UnsupportedOperationException.class, () -> scene.ext().put("k2", "v2"));
    }

    @Test
    void blankCodeRejected() {
        assertThrows(InvalidRequestException.class,
                () -> new SceneDefinition(" ", ContentType.TEXT, null, null, null));
    }
}
