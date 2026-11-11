package io.github.wrqtec.content.safe.starter.config;

import java.nio.charset.StandardCharsets;

import io.github.wrqtec.content.safe.api.enums.ContentType;
import io.github.wrqtec.content.safe.api.exception.ConfigInvalidException;
import io.github.wrqtec.content.safe.api.exception.ErrorCodes;
import io.github.wrqtec.content.safe.model.SceneDefinition;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.ClassPathResource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JsonSceneDefinitionProviderTest {

    private static ByteArrayResource json(String content) {
        return new ByteArrayResource(content.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void missingResourceReturnsEmpty() {
        JsonSceneDefinitionProvider provider =
                new JsonSceneDefinitionProvider(new ClassPathResource("content-safe/not-exists.json"));
        assertTrue(provider.scenes().isEmpty());
    }

    @Test
    void validJsonParses() {
        JsonSceneDefinitionProvider provider = new JsonSceneDefinitionProvider(json("""
                {"schemaVersion":1,"scenes":[
                  {"code":"comment","type":"TEXT","defaultSeverity":"high","ext":{"k":"v"}},
                  {"code":"avatar","type":"IMAGE"}
                ]}"""));
        assertEquals(2, provider.scenes().size());
        SceneDefinition comment = provider.scenes().get(0);
        assertEquals("comment", comment.code());
        assertEquals(ContentType.TEXT, comment.type());
        assertEquals(io.github.wrqtec.content.safe.api.enums.Severity.HIGH, comment.defaultSeverity());
        assertEquals("v", comment.ext().get("k"));
    }

    @Test
    void brokenJsonFailsFast() {
        ConfigInvalidException e = assertThrows(ConfigInvalidException.class,
                () -> new JsonSceneDefinitionProvider(json("{not-json")));
        assertEquals(ErrorCodes.CONFIG_INVALID, e.getCode());
    }

    @Test
    void unsupportedSchemaVersionFailsFast() {
        ConfigInvalidException e = assertThrows(ConfigInvalidException.class,
                () -> new JsonSceneDefinitionProvider(json("{\"schemaVersion\":2,\"scenes\":[]}")));
        assertTrue(e.getMessage().contains("schemaVersion"));
    }

    @Test
    void videoSceneParses() {
        JsonSceneDefinitionProvider provider = new JsonSceneDefinitionProvider(json(
                "{\"schemaVersion\":1,\"scenes\":[{\"code\":\"short_video\",\"type\":\"VIDEO\"}]}"));
        assertEquals(1, provider.scenes().size());
        assertEquals(ContentType.VIDEO, provider.scenes().get(0).type());
    }

    @Test
    void unknownTypeFailsFast() {
        ConfigInvalidException e = assertThrows(ConfigInvalidException.class,
                () -> new JsonSceneDefinitionProvider(json(
                        "{\"schemaVersion\":1,\"scenes\":[{\"code\":\"v\",\"type\":\"PICTURE\"}]}")));
        assertTrue(e.getMessage().contains("PICTURE"));
    }

    @Test
    void missingCodeFailsFast() {
        assertThrows(ConfigInvalidException.class,
                () -> new JsonSceneDefinitionProvider(json(
                        "{\"schemaVersion\":1,\"scenes\":[{\"type\":\"TEXT\"}]}")));
    }
}
