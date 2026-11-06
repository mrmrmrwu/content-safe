package io.github.wrqtec.content.safe.provider.volcengine.internal;

import java.time.Duration;
import java.util.Map;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.volcengine.model.request.RiskDetectionRequest;
import com.volcengine.model.response.TextRiskResponse;
import io.github.wrqtec.content.safe.api.AuditRequest;
import io.github.wrqtec.content.safe.api.AuditResult;
import io.github.wrqtec.content.safe.api.ContentRef;
import io.github.wrqtec.content.safe.api.enums.ContentType;
import io.github.wrqtec.content.safe.api.enums.RiskSuggestion;
import io.github.wrqtec.content.safe.api.exception.InvalidRequestException;
import io.github.wrqtec.content.safe.api.exception.ProviderCallException;
import io.github.wrqtec.content.safe.api.exception.UnsupportedContentException;
import io.github.wrqtec.content.safe.model.AuditCapabilities;
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

class VolcengineTextRiskOperationTest {

    private final VolcengineClientInvoker invoker = Mockito.mock(VolcengineClientInvoker.class);
    private final VolcengineAuditResultNormalizer normalizer = new VolcengineAuditResultNormalizer();

    private final ProviderPlan plan = VolcengineAuditOperationProvider.syncPlan(
            "TextRisk", "svc-nickname", "2022-01-26", VolcengineLimits.textRisk());

    private final SceneDefinition scene = new SceneDefinition("nickname", ContentType.TEXT,
            null, null, Map.of("scene", "chat"));

    @Test
    void planAndCapabilitiesDeclared() {
        VolcengineTextRiskOperation operation =
                new VolcengineTextRiskOperation(plan, 6801, scene, "text", invoker, normalizer);
        assertThat(operation.plan()).isSameAs(plan);
        AuditCapabilities capabilities = operation.capabilities();
        assertThat(capabilities.sync()).isTrue();
        assertThat(capabilities.maxBatchSize()).isEqualTo(1);
    }

    @Test
    void auditBuildsEnvelopeAndNormalizes() throws Exception {
        TextRiskResponse response = new TextRiskResponse();
        TextRiskResponse.RiskDetectionResult result = new TextRiskResponse.RiskDetectionResult();
        TextRiskResponse.DecisionData data = new TextRiskResponse.DecisionData();
        data.setDecision("pass");
        result.setRequestId("req-t-1");
        result.setData(data);
        response.setResult(result);
        when(invoker.textRisk(any())).thenReturn(response);

        VolcengineTextRiskOperation operation =
                new VolcengineTextRiskOperation(plan, 6801, scene, "text", invoker, normalizer);
        AuditRequest request = AuditRequest.builder()
                .scene("nickname").type(ContentType.TEXT)
                .content(ContentRef.text("hello world")).dataId("d-1").build();

        AuditResult auditResult = operation.audit(request);

        assertThat(auditResult.suggestion()).isEqualTo(RiskSuggestion.PASS);
        assertThat(auditResult.dataId()).isEqualTo("d-1");
        assertThat(auditResult.cost()).isNotNull();
        ArgumentCaptor<RiskDetectionRequest> captor = ArgumentCaptor.forClass(RiskDetectionRequest.class);
        Mockito.verify(invoker).textRisk(captor.capture());
        RiskDetectionRequest sdkRequest = captor.getValue();
        assertThat(sdkRequest.getAppId()).isEqualTo(6801);
        assertThat(sdkRequest.getService()).isEqualTo("svc-nickname");
        JsonObject parameters = JsonParser.parseString(sdkRequest.getParameters()).getAsJsonObject();
        assertThat(parameters.get("text").getAsString()).isEqualTo("hello world");
        assertThat(parameters.get("scene").getAsString()).isEqualTo("chat");
    }

    @Test
    void rejectsNonTextContent() {
        VolcengineTextRiskOperation operation =
                new VolcengineTextRiskOperation(plan, 6801, scene, "text", invoker, normalizer);
        AuditRequest request = AuditRequest.image("avatar", ContentRef.url("https://example.com/a.png"));
        assertThatThrownBy(() -> operation.audit(request))
                .isInstanceOf(UnsupportedContentException.class)
                .hasMessageContaining("only supports text");
        Mockito.verifyNoInteractions(invoker);
    }

    @Test
    void invokerExceptionMapsToControlled() throws Exception {
        when(invoker.textRisk(any())).thenThrow(new IllegalStateException("connect refused"));
        VolcengineTextRiskOperation operation =
                new VolcengineTextRiskOperation(plan, 6801, scene, "text", invoker, normalizer);
        assertThatThrownBy(() -> operation.audit(AuditRequest.text("nickname", "hello")))
                .isInstanceOf(ProviderCallException.class)
                .hasMessageContaining("volcengine content security call failed");
    }

    @Test
    void missingContentKeyFailsAtConstruction() {
        assertThatThrownBy(() -> new VolcengineTextRiskOperation(plan, 6801, scene, " ", invoker, normalizer))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("contentKey");
    }

    @Test
    void limitsDeclareSingleItemAndTimeout() {
        assertThat(VolcengineLimits.textRisk().timeout()).isEqualTo(Duration.ofSeconds(5));
        assertThat(VolcengineLimits.imageContentRisk().timeout()).isEqualTo(Duration.ofSeconds(5));
    }
}
