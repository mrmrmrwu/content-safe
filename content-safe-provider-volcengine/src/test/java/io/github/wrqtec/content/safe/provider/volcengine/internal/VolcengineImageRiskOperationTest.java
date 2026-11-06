package io.github.wrqtec.content.safe.provider.volcengine.internal;

import java.util.Map;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.volcengine.model.request.RiskDetectionRequest;
import com.volcengine.model.response.ImageRiskDetectionResponse;
import io.github.wrqtec.content.safe.api.AuditRequest;
import io.github.wrqtec.content.safe.api.AuditResult;
import io.github.wrqtec.content.safe.api.ContentRef;
import io.github.wrqtec.content.safe.api.enums.ContentType;
import io.github.wrqtec.content.safe.api.enums.RiskSuggestion;
import io.github.wrqtec.content.safe.api.exception.UnsupportedContentException;
import io.github.wrqtec.content.safe.model.ProviderPlan;
import io.github.wrqtec.content.safe.model.SceneDefinition;
import io.github.wrqtec.content.safe.provider.volcengine.VolcengineAuditOperationProvider;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

class VolcengineImageRiskOperationTest {

    private final VolcengineClientInvoker invoker = Mockito.mock(VolcengineClientInvoker.class);
    private final VolcengineAuditResultNormalizer normalizer = new VolcengineAuditResultNormalizer();

    private final ProviderPlan plan = VolcengineAuditOperationProvider.syncPlan(
            "ImageContentRiskV2", "svc-avatar", "2021-11-29", VolcengineLimits.imageContentRisk());

    private final SceneDefinition scene = new SceneDefinition("avatar", ContentType.IMAGE,
            null, null, Map.of("type", "static"));

    @Test
    void urlContentGoesIntoConfiguredKey() throws Exception {
        when(invoker.imageContentRiskV2(any())).thenReturn(passResponse());
        VolcengineImageRiskOperation operation =
                new VolcengineImageRiskOperation(plan, 6801, scene, "image", invoker, normalizer);

        AuditResult result = operation.audit(AuditRequest.image("avatar", ContentRef.url("https://cdn.example.com/a.png")));

        assertThat(result.suggestion()).isEqualTo(RiskSuggestion.PASS);
        ArgumentCaptor<RiskDetectionRequest> captor = ArgumentCaptor.forClass(RiskDetectionRequest.class);
        Mockito.verify(invoker).imageContentRiskV2(captor.capture());
        JsonObject parameters = JsonParser.parseString(captor.getValue().getParameters()).getAsJsonObject();
        assertThat(parameters.get("image").getAsString()).isEqualTo("https://cdn.example.com/a.png");
        assertThat(parameters.get("type").getAsString()).isEqualTo("static");
    }

    @Test
    void base64ContentGoesIntoConfiguredKey() throws Exception {
        when(invoker.imageContentRiskV2(any())).thenReturn(passResponse());
        VolcengineImageRiskOperation operation =
                new VolcengineImageRiskOperation(plan, 6801, scene, "image", invoker, normalizer);

        operation.audit(AuditRequest.image("avatar", ContentRef.base64("aGVsbG8=")));

        ArgumentCaptor<RiskDetectionRequest> captor = ArgumentCaptor.forClass(RiskDetectionRequest.class);
        Mockito.verify(invoker).imageContentRiskV2(captor.capture());
        JsonObject parameters = JsonParser.parseString(captor.getValue().getParameters()).getAsJsonObject();
        assertThat(parameters.get("image").getAsString()).isEqualTo("aGVsbG8=");
    }

    @Test
    void bytesContentEncodedAsBase64() throws Exception {
        when(invoker.imageContentRiskV2(any())).thenReturn(passResponse());
        VolcengineImageRiskOperation operation =
                new VolcengineImageRiskOperation(plan, 6801, scene, "image", invoker, normalizer);

        operation.audit(AuditRequest.image("avatar",
                ContentRef.bytes("hello".getBytes(java.nio.charset.StandardCharsets.UTF_8))));

        ArgumentCaptor<RiskDetectionRequest> captor = ArgumentCaptor.forClass(RiskDetectionRequest.class);
        Mockito.verify(invoker).imageContentRiskV2(captor.capture());
        JsonObject parameters = JsonParser.parseString(captor.getValue().getParameters()).getAsJsonObject();
        assertThat(parameters.get("image").getAsString()).isEqualTo("aGVsbG8=");
    }

    @Test
    void rejectsTextContent() {
        VolcengineImageRiskOperation operation =
                new VolcengineImageRiskOperation(plan, 6801, scene, "image", invoker, normalizer);
        assertThatThrownBy(() -> operation.audit(AuditRequest.text("nickname", "hello")))
                .isInstanceOf(UnsupportedContentException.class)
                .hasMessageContaining("supports url/base64/bytes");
        Mockito.verifyNoInteractions(invoker);
    }

    private ImageRiskDetectionResponse passResponse() {
        ImageRiskDetectionResponse response = new ImageRiskDetectionResponse();
        ImageRiskDetectionResponse.ImageDetectionResult result = new ImageRiskDetectionResponse.ImageDetectionResult();
        ImageRiskDetectionResponse.ImageDetectionData data = new ImageRiskDetectionResponse.ImageDetectionData();
        data.setDecision("pass");
        result.setRequestId("req-i-1");
        result.setData(data);
        response.setResult(result);
        return response;
    }
}
