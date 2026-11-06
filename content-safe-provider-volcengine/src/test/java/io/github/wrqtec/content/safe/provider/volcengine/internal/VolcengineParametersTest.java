package io.github.wrqtec.content.safe.provider.volcengine.internal;

import java.util.Map;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.volcengine.model.request.RiskDetectionRequest;
import io.github.wrqtec.content.safe.api.exception.ConfigInvalidException;
import io.github.wrqtec.content.safe.api.exception.InvalidRequestException;
import io.github.wrqtec.content.safe.api.enums.ContentType;
import io.github.wrqtec.content.safe.model.SceneDefinition;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class VolcengineParametersTest {

    @Test
    void envelopeCarriesAppIdServiceAndParameters() {
        SceneDefinition scene = new SceneDefinition("nickname", ContentType.TEXT, null, null, null);
        RiskDetectionRequest request = VolcengineParameters.envelope(6801, "svc-nickname", "text", "hello", scene);
        assertThat(request.getAppId()).isEqualTo(6801);
        assertThat(request.getService()).isEqualTo("svc-nickname");
        assertThat(JsonParser.parseString(request.getParameters()).getAsJsonObject().get("text").getAsString())
                .isEqualTo("hello");
    }

    @Test
    void sceneExtIsPassedThrough() {
        SceneDefinition scene = new SceneDefinition("nickname", ContentType.TEXT, null, null,
                Map.of("scene", "chat", "ttl", "300"));
        String json = VolcengineParameters.toJson("text", "hello", scene);
        JsonObject payload = JsonParser.parseString(json).getAsJsonObject();
        assertThat(payload.get("scene").getAsString()).isEqualTo("chat");
        assertThat(payload.get("ttl").getAsString()).isEqualTo("300");
        assertThat(payload.get("text").getAsString()).isEqualTo("hello");
    }

    @Test
    void missingContentKeyFailsFastWithReadableMessage() {
        assertThatThrownBy(() -> VolcengineParameters.toJson(null, "hello", null))
                .isInstanceOf(ConfigInvalidException.class)
                .hasMessageContaining("parameters-text-key");
        assertThatThrownBy(() -> VolcengineParameters.toJson("  ", "hello", null))
                .isInstanceOf(ConfigInvalidException.class);
    }

    @Test
    void envelopeRequiresAppIdAndService() {
        assertThatThrownBy(() -> VolcengineParameters.envelope(null, "svc", "text", "x", null))
                .isInstanceOf(InvalidRequestException.class);
        assertThatThrownBy(() -> VolcengineParameters.envelope(1, " ", "text", "x", null))
                .isInstanceOf(InvalidRequestException.class);
    }

    @Test
    void contentKeyFromExtra() {
        assertThat(VolcengineParameters.contentKey(Map.of("parameters-text-key", "content"),
                "parameters-text-key")).isEqualTo("content");
        assertThat(VolcengineParameters.contentKey(null, "parameters-text-key")).isNull();
        assertThat(VolcengineParameters.contentKey(Map.of(), "parameters-text-key")).isNull();
    }
}
