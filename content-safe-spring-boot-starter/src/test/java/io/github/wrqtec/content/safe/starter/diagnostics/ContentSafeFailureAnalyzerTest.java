package io.github.wrqtec.content.safe.starter.diagnostics;

import io.github.wrqtec.content.safe.api.exception.AllProvidersFailedException;
import io.github.wrqtec.content.safe.api.exception.ConfigInvalidException;
import io.github.wrqtec.content.safe.api.exception.ErrorCodes;
import io.github.wrqtec.content.safe.api.exception.InvalidRequestException;
import io.github.wrqtec.content.safe.api.exception.ProviderCallException;
import io.github.wrqtec.content.safe.api.exception.ProviderNotAvailableException;
import io.github.wrqtec.content.safe.api.exception.SceneNotConfiguredException;
import io.github.wrqtec.content.safe.api.exception.UnsupportedContentException;
import io.github.wrqtec.content.safe.api.exception.UnsupportedFeatureException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.BeanCreationException;
import org.springframework.boot.diagnostics.FailureAnalysis;

import static org.assertj.core.api.Assertions.assertThat;

class ContentSafeFailureAnalyzerTest {

    private final ContentSafeFailureAnalyzer analyzer = new ContentSafeFailureAnalyzer();

    @Test
    void declinesNonContentSafeFailures() {
        assertThat(analyzer.analyze(new IllegalStateException("boom"))).isNull();
    }

    @Test
    void missingCredentialsGetsCredentialHintAndBeanName() {
        ConfigInvalidException cause = new ConfigInvalidException("missing credentials for provider 'aliyun': "
                + "configure content.safe.providers.aliyun.ak/sk or env CONTENT_SAFE_ALIYUN_AK/_SK");
        BeanCreationException failure = new BeanCreationException("aliyunOperationProvider", "create", cause);

        FailureAnalysis analysis = analyzer.analyze(failure);

        assertThat(analysis).isNotNull();
        assertThat(analysis.getDescription())
                .contains(ErrorCodes.CONFIG_INVALID)
                .contains("aliyunOperationProvider")
                .contains(cause.getMessage());
        assertThat(analysis.getAction()).contains("CredentialsProvider");
    }

    @Test
    void unknownArbitrationPolicyGetsPolicyHint() {
        ConfigInvalidException cause = new ConfigInvalidException(
                "unknown arbitration policy: NOPE (available: [FIRST_NON_PASS])");

        FailureAnalysis analysis = analyzer.analyze(cause);

        assertThat(analysis).isNotNull();
        assertThat(analysis.getAction()).contains("AuditArbitrationPolicy").contains("FIRST_NON_PASS");
    }

    @Test
    void scenesSchemaVersionGetsMigrationHint() {
        ConfigInvalidException cause = new ConfigInvalidException(
                "unsupported scenes.json schemaVersion: 2 (supported: 1)");

        FailureAnalysis analysis = analyzer.analyze(cause);

        assertThat(analysis).isNotNull();
        assertThat(analysis.getAction()).contains("scenes.json").contains("migration");
    }

    @Test
    void configSchemaVersionGetsMigrationHint() {
        ConfigInvalidException cause = new ConfigInvalidException(
                "unsupported content.safe.schema-version: 2 (supported: 1)");

        FailureAnalysis analysis = analyzer.analyze(cause);

        assertThat(analysis).isNotNull();
        assertThat(analysis.getAction()).contains("content.safe.schema-version");
    }

    @Test
    void sceneNotConfiguredPointsAtSceneRegistration() {
        SceneNotConfiguredException cause = new SceneNotConfiguredException("scene not configured: vip_sign");

        FailureAnalysis analysis = analyzer.analyze(cause);

        assertThat(analysis).isNotNull();
        assertThat(analysis.getDescription()).contains(ErrorCodes.SCENE_NOT_CONFIGURED);
        assertThat(analysis.getAction()).contains("scenes.json").contains("SceneDefinitionProvider");
    }

    @Test
    void unsupportedFeaturePointsAtFeatureMatrix() {
        UnsupportedFeatureException cause = new UnsupportedFeatureException("feature not supported: batch");

        FailureAnalysis analysis = analyzer.analyze(cause);

        assertThat(analysis).isNotNull();
        assertThat(analysis.getAction()).contains("provider-feature-matrix");
    }

    @Test
    void unsupportedContentPointsAtFeatureMatrix() {
        UnsupportedContentException cause = new UnsupportedContentException("no provider supports base64 image");

        FailureAnalysis analysis = analyzer.analyze(cause);

        assertThat(analysis).isNotNull();
        assertThat(analysis.getAction()).contains("provider-feature-matrix");
    }

    @Test
    void runtimeErrorCodesGetTailoredHints() {
        assertThat(analyzer.analyze(new ProviderNotAvailableException("all candidates circuit-open"))
                .getAction()).contains("circuit-open");
        assertThat(analyzer.analyze(new ProviderCallException("aliyun:TextModeration timeout"))
                .getAction()).contains("error-code-table.md");
        assertThat(analyzer.analyze(new AllProvidersFailedException("aliyun:TextModeration failed"))
                .getAction()).contains("failover");
        assertThat(analyzer.analyze(new InvalidRequestException("unknown taskId: t-1"))
                .getAction()).contains("payload");
    }

    @Test
    void unwrappedFailureOmitsBeanName() {
        ConfigInvalidException cause = new ConfigInvalidException("bad config");

        FailureAnalysis analysis = analyzer.analyze(cause);

        assertThat(analysis).isNotNull();
        assertThat(analysis.getDescription()).doesNotContain("while creating bean");
        assertThat(analysis.getAction()).contains("content-safe-starter-guide.md");
    }
}
