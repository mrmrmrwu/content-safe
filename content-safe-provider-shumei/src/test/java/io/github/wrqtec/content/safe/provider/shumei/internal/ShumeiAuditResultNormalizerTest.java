package io.github.wrqtec.content.safe.provider.shumei.internal;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import com.google.gson.Gson;
import io.github.wrqtec.content.safe.api.AuditResult;
import io.github.wrqtec.content.safe.api.enums.HitSource;
import io.github.wrqtec.content.safe.api.enums.RiskSuggestion;
import io.github.wrqtec.content.safe.api.exception.ProviderCallException;
import io.github.wrqtec.content.safe.model.ProviderPlan;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link ShumeiAuditResultNormalizer} 单元测试
 *
 * @author L.David
 */
class ShumeiAuditResultNormalizerTest {

    private static final Gson GSON = new Gson();

    private final ShumeiAuditResultNormalizer normalizer = new ShumeiAuditResultNormalizer();

    @Test
    void providerIsShumei() {
        assertThat(normalizer.provider()).isEqualTo("shumei");
    }

    @Test
    void suggestionMapping() {
        assertThat(ShumeiAuditResultNormalizer.suggestionOf("PASS")).isEqualTo(RiskSuggestion.PASS);
        assertThat(ShumeiAuditResultNormalizer.suggestionOf("reject")).isEqualTo(RiskSuggestion.REJECT);
        assertThat(ShumeiAuditResultNormalizer.suggestionOf("Review")).isEqualTo(RiskSuggestion.REVIEW);
    }

    @Test
    void unknownRiskLevelFallsBackToReview() {
        assertThat(ShumeiAuditResultNormalizer.suggestionOf("MACHINE_HIT")).isEqualTo(RiskSuggestion.REVIEW);
        assertThat(ShumeiAuditResultNormalizer.suggestionOf("")).isEqualTo(RiskSuggestion.REVIEW);
        assertThat(ShumeiAuditResultNormalizer.suggestionOf(null)).isEqualTo(RiskSuggestion.REVIEW);
    }

    @Test
    void finalResultMapping() {
        assertThat(ShumeiAuditResultNormalizer.finalResultOf(1)).isTrue();
        assertThat(ShumeiAuditResultNormalizer.finalResultOf(0)).isFalse();
        assertThat(ShumeiAuditResultNormalizer.finalResultOf(null)).isTrue();
    }

    @Test
    void hitSourceMapping() {
        assertThat(ShumeiAuditResultNormalizer.hitSourceOf(0)).isEqualTo(HitSource.MACHINE);
        assertThat(ShumeiAuditResultNormalizer.hitSourceOf(1)).isEqualTo(HitSource.MANUAL);
        assertThat(ShumeiAuditResultNormalizer.hitSourceOf(null)).isEqualTo(HitSource.MACHINE);
    }

    @Test
    void passWithoutLabelsProducesNoHits() {
        ShumeiSyncResponse response = GSON.fromJson(
                "{\"code\":1100,\"riskLevel\":\"PASS\",\"riskLabel1\":\"normal\",\"allLabels\":[]}",
                ShumeiSyncResponse.class);
        AuditResult result = normalizer.normalize(response, context("text", "d-1"));

        assertThat(result.ok()).isTrue();
        assertThat(result.suggestion()).isEqualTo(RiskSuggestion.PASS);
        assertThat(result.hits()).isEmpty();
        assertThat(result.provider()).isEqualTo("shumei");
        assertThat(result.service()).isEqualTo("text");
        assertThat(result.dataId()).isEqualTo("d-1");
    }

    @Test
    void nonFinalResultPropagated() {
        ShumeiSyncResponse response = GSON.fromJson(
                "{\"code\":1100,\"riskLevel\":\"REVIEW\",\"finalResult\":0,\"resultType\":0,\"allLabels\":[]}",
                ShumeiSyncResponse.class);
        AuditResult result = normalizer.normalize(response, context("text", null));

        assertThat(result.finalResult()).isFalse();
        assertThat(result.raw()).containsEntry("finalResult", 0);
    }

    @Test
    void reviewWithAllLabelsProducesHits() {
        ShumeiSyncResponse response = GSON.fromJson(
                "{\"code\":1100,\"riskLevel\":\"REVIEW\",\"resultType\":0,\"allLabels\":[{"
                        + "\"probability\":0.9,\"riskLevel\":\"REVIEW\",\"riskLabel1\":\"ad\","
                        + "\"riskLabel2\":\"jiahaoyou\",\"riskLabel3\":\"jiahaoyou\"}]}",
                ShumeiSyncResponse.class);
        AuditResult result = normalizer.normalize(response, context("text", "d-2"));

        assertThat(result.suggestion()).isEqualTo(RiskSuggestion.REVIEW);
        assertThat(result.hits()).hasSize(1);
        assertThat(result.hits().get(0).labelPath()).isEqualTo("ad/jiahaoyou/jiahaoyou");
        assertThat(result.hits().get(0).labels()).containsExactly("ad", "jiahaoyou", "jiahaoyou");
        assertThat(result.hits().get(0).confidence()).isEqualTo(0.9D);
        assertThat(result.hits().get(0).source()).isEqualTo(HitSource.MACHINE);
    }

    @Test
    void passEntriesInsideAllLabelsSkipped() {
        ShumeiSyncResponse response = GSON.fromJson(
                "{\"code\":1100,\"riskLevel\":\"REVIEW\",\"allLabels\":["
                        + "{\"probability\":0.1,\"riskLevel\":\"PASS\",\"riskLabel1\":\"normal\"},"
                        + "{\"probability\":0.9,\"riskLevel\":\"REJECT\",\"riskLabel1\":\"polity\"}]}",
                ShumeiSyncResponse.class);
        AuditResult result = normalizer.normalize(response, context("text", null));

        assertThat(result.hits()).hasSize(1);
        assertThat(result.hits().get(0).labelPath()).isEqualTo("polity");
    }

    @Test
    void nonPassRootLabelWithoutAllLabelsProducesFallbackHit() {
        ShumeiSyncResponse response = GSON.fromJson(
                "{\"code\":1100,\"riskLevel\":\"REJECT\",\"riskLabel1\":\"porn\",\"riskLabel2\":\"ad\"}",
                ShumeiSyncResponse.class);
        AuditResult result = normalizer.normalize(response, context("text", null));

        assertThat(result.suggestion()).isEqualTo(RiskSuggestion.REJECT);
        assertThat(result.hits()).hasSize(1);
        assertThat(result.hits().get(0).labelPath()).isEqualTo("porn/ad");
        assertThat(result.hits().get(0).confidence()).isNull();
    }

    @Test
    void nonPassRootLabelWithoutLabelProducesNoHits() {
        ShumeiSyncResponse response = GSON.fromJson(
                "{\"code\":1100,\"riskLevel\":\"REVIEW\",\"riskDetail\":{}}",
                ShumeiSyncResponse.class);
        AuditResult result = normalizer.normalize(response, context("text", null));

        assertThat(result.hits()).isEmpty();
    }

    @Test
    void hitRawKeepsRiskSourceAndMatchedWordsOnly() {
        ShumeiSyncResponse response = GSON.fromJson(
                "{\"code\":1100,\"riskLevel\":\"REJECT\",\"riskLabel1\":\"ad\","
                        + "\"riskLabel2\":\"jiahaoyou\",\"riskDetail\":{"
                        + "\"riskSource\":1001,"
                        + "\"matchedLists\":[{\"name\":\"名单\",\"words\":[{\"word\":\"qq\",\"position\":[6,7]}]}],"
                        + "\"riskSegments\":[{\"segment\":\"qq12345\"}],"
                        + "\"faces\":[{\"name\":\"某人\",\"probability\":0.9}]},"
                        + "\"allLabels\":[]}",
                ShumeiSyncResponse.class);
        AuditResult result = normalizer.normalize(response, context("text", null));

        assertThat(result.hits()).hasSize(1);
        Map<String, Object> raw = result.hits().get(0).raw();
        assertThat(raw).containsEntry("riskLevel", "REJECT").containsEntry("riskSource", 1001);
        com.google.gson.JsonArray matchedWords = (com.google.gson.JsonArray) raw.get("matchedWords");
        assertThat(matchedWords).hasSize(1);
        assertThat(matchedWords.get(0).toString()).contains("qq");
        // 用户内容原文（片段/人脸名）不透传
        assertThat(raw.toString()).doesNotContain("qq12345").doesNotContain("某人");
        assertThat(result.raw().toString()).doesNotContain("qq12345");
    }

    @Test
    void imageRejectKeepsImageRiskSource() {
        ShumeiSyncResponse response = GSON.fromJson(
                "{\"code\":1100,\"riskLevel\":\"REJECT\",\"riskDetail\":{\"riskSource\":1002},"
                        + "\"allLabels\":[{\"probability\":0.988,\"riskLevel\":\"REJECT\","
                        + "\"riskLabel1\":\"polity\",\"riskLabel2\":\"politician\"}]}",
                ShumeiSyncResponse.class);
        AuditResult result = normalizer.normalize(response, context("image", null));

        assertThat(result.hits()).hasSize(1);
        assertThat(result.hits().get(0).raw()).containsEntry("riskSource", 1002);
    }

    @Test
    void videoHitsSkipNullListElements() {
        // Gson 可反序列化出含 null 元素的数组（[null, {...}]），归一化必须跳过而不是 NPE
        ShumeiVideoQueryResponse response = GSON.fromJson(
                "{\"code\":1100,\"requestId\":\"req-null-guard\",\"riskLevel\":null,\"auxInfo\":{"
                        + "\"frameDetail\":[null,"
                        + "{\"riskLevel\":\"REJECT\",\"riskLabel1\":\"polity\",\"allLabels\":[null,"
                        + "{\"probability\":0.97,\"riskLevel\":\"REJECT\",\"riskLabel1\":\"polity\","
                        + "\"riskLabel2\":\"politician\"}]},"
                        + "{\"riskLevel\":\"PASS\"}],"
                        + "\"audioDetail\":[null,{\"riskLevel\":\"REVIEW\",\"riskLabel1\":\"ad\"}]}}",
                ShumeiVideoQueryResponse.class);
        AuditResult result = normalizer.normalizeVideoQuery(response, context("video", "data-x"));

        assertThat(result.suggestion()).isEqualTo(RiskSuggestion.REVIEW);
        assertThat(result.hits()).hasSize(2);
        assertThat(result.hits().get(0).labelPath()).isEqualTo("polity/politician");
        assertThat(result.hits().get(0).raw()).containsEntry("kind", "frame");
        assertThat(result.hits().get(1).labelPath()).isEqualTo("ad");
        assertThat(result.hits().get(1).raw()).containsEntry("kind", "audio");
    }

    @Test
    void unexpectedRawTypeThrows() {
        assertThatThrownBy(() -> normalizer.normalize(new Object(), context("text", null)))
                .isInstanceOf(ProviderCallException.class);
    }

    @Test
    void contextContainsEventIdDataIdAndCost() {
        Map<String, Object> context = ShumeiAuditResultNormalizer.context(
                ShumeiProviderPlanFixture.textPlan("text"), "data-x", Duration.ofMillis(5));

        assertThat(context)
                .containsEntry(ShumeiAuditResultNormalizer.CONTEXT_EVENT_ID, "text")
                .containsEntry(ShumeiAuditResultNormalizer.CONTEXT_DATA_ID, "data-x")
                .containsEntry(ShumeiAuditResultNormalizer.CONTEXT_COST, Duration.ofMillis(5));
    }

    @Test
    void contextSkipsNullValues() {
        Map<String, Object> context = ShumeiAuditResultNormalizer.context(
                ShumeiProviderPlanFixture.textPlan("text"), null, null);

        assertThat(context).hasSize(1).containsKey(ShumeiAuditResultNormalizer.CONTEXT_EVENT_ID);
    }

    @Test
    void rawOnlyContainsCodeFinalResultAndResultType() {
        ShumeiSyncResponse response = GSON.fromJson(
                "{\"code\":1100,\"requestId\":\"r-1\",\"riskLevel\":\"PASS\",\"finalResult\":1,\"resultType\":0}",
                ShumeiSyncResponse.class);
        AuditResult result = normalizer.normalize(response, context("text", null));

        assertThat(result.raw()).containsOnly(
                Map.entry("code", 1100),
                Map.entry("finalResult", 1),
                Map.entry("resultType", 0));
        assertThat(result.requestId()).isEqualTo("r-1");
    }

    private static Map<String, Object> context(String eventId, String dataId) {
        ProviderPlan plan = ShumeiProviderPlanFixture.textPlan(eventId);
        return ShumeiAuditResultNormalizer.context(plan, dataId, Duration.ofMillis(12));
    }
}
