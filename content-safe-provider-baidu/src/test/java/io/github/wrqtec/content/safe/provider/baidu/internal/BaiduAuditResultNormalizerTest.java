package io.github.wrqtec.content.safe.provider.baidu.internal;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import com.google.gson.Gson;
import io.github.wrqtec.content.safe.api.AuditResult;
import io.github.wrqtec.content.safe.api.enums.RiskSuggestion;
import io.github.wrqtec.content.safe.api.exception.ProviderCallException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link BaiduAuditResultNormalizer} 单元测试
 *
 * @author L.David
 */
class BaiduAuditResultNormalizerTest {

    private static final Gson GSON = new Gson();

    private final BaiduAuditResultNormalizer normalizer = new BaiduAuditResultNormalizer();

    @Test
    void providerIsBaidu() {
        assertThat(normalizer.provider()).isEqualTo("baidu");
    }

    @Test
    void suggestionMapping() {
        assertThat(BaiduAuditResultNormalizer.suggestionOf(1)).isEqualTo(RiskSuggestion.PASS);
        assertThat(BaiduAuditResultNormalizer.suggestionOf(2)).isEqualTo(RiskSuggestion.REJECT);
        assertThat(BaiduAuditResultNormalizer.suggestionOf(3)).isEqualTo(RiskSuggestion.REVIEW);
        assertThat(BaiduAuditResultNormalizer.suggestionOf(4)).isEqualTo(RiskSuggestion.ERROR);
    }

    @Test
    void unknownConclusionTypeFallsBackToReject() {
        assertThat(BaiduAuditResultNormalizer.suggestionOf(99)).isEqualTo(RiskSuggestion.REJECT);
        assertThat(BaiduAuditResultNormalizer.suggestionOf(null)).isEqualTo(RiskSuggestion.REJECT);
    }

    @Test
    void auditFailureKeepsOkTrueWithErrorSuggestion() {
        BaiduCensorResponse response = GSON.fromJson(
                "{\"log_id\":42,\"conclusion\":\"审核失败\",\"conclusionType\":4}", BaiduCensorResponse.class);
        AuditResult result = normalizer.normalize(response,
                BaiduAuditResultNormalizer.context(BaiduProviderPlanFixture.textPlan("1"), null, null));

        assertThat(result.ok()).isTrue();
        assertThat(result.suggestion()).isEqualTo(RiskSuggestion.ERROR);
        assertThat(result.requestId()).isEqualTo("42");
    }

    @Test
    void emptyDataListPasses() {
        BaiduCensorResponse response = GSON.fromJson(
                "{\"log_id\":7,\"conclusionType\":1,\"data\":[]}", BaiduCensorResponse.class);
        AuditResult result = normalizer.normalize(response,
                BaiduAuditResultNormalizer.context(BaiduProviderPlanFixture.textPlan("1"), "d-1", null));

        assertThat(result.suggestion()).isEqualTo(RiskSuggestion.PASS);
        assertThat(result.hits()).isEmpty();
        assertThat(result.dataId()).isEqualTo("d-1");
    }

    @Test
    void dataWithoutMsgProducesEmptyLabels() {
        BaiduCensorResponse response = GSON.fromJson(
                "{\"log_id\":8,\"conclusionType\":2,\"data\":[{\"type\":2,\"subType\":0,\"probability\":0.5}]}",
                BaiduCensorResponse.class);
        AuditResult result = normalizer.normalize(response,
                BaiduAuditResultNormalizer.context(BaiduProviderPlanFixture.imagePlan("1"), null, null));

        assertThat(result.hits()).hasSize(1);
        assertThat(result.hits().get(0).labels()).isEmpty();
        assertThat(result.hits().get(0).evidence()).isNull();
        assertThat(result.hits().get(0).labelPath()).isEqualTo("2.0");
    }

    @Test
    void unexpectedRawTypeThrows() {
        Map<String, Object> context = BaiduAuditResultNormalizer.context(
                BaiduProviderPlanFixture.textPlan("1"), null, Duration.ofMillis(1));
        assertThatThrownBy(() -> normalizer.normalize(new Object(), context))
                .isInstanceOf(ProviderCallException.class);
    }

    @Test
    void contextContainsServiceDataIdAndCost() {
        Map<String, Object> context = BaiduAuditResultNormalizer.context(
                BaiduProviderPlanFixture.textPlan("205"), "data-x", Duration.ofMillis(5));

        assertThat(context).containsEntry(BaiduAuditResultNormalizer.CONTEXT_SERVICE, "205")
                .containsEntry(BaiduAuditResultNormalizer.CONTEXT_DATA_ID, "data-x")
                .containsEntry(BaiduAuditResultNormalizer.CONTEXT_COST, Duration.ofMillis(5));
    }

    @Test
    void contextSkipsNullValues() {
        Map<String, Object> context = BaiduAuditResultNormalizer.context(
                BaiduProviderPlanFixture.textPlan("205"), null, null);

        assertThat(context).hasSize(1).containsKey(BaiduAuditResultNormalizer.CONTEXT_SERVICE);
    }

    @Test
    void rawOnlyContainsLogId() {
        BaiduCensorResponse response = GSON.fromJson(
                "{\"log_id\":99,\"conclusionType\":1}", BaiduCensorResponse.class);
        AuditResult result = normalizer.normalize(response,
                BaiduAuditResultNormalizer.context(BaiduProviderPlanFixture.textPlan("1"), null, null));

        assertThat(result.raw()).containsOnly(Map.entry("log_id", 99L));
    }

    @Test
    void hitsOfMultipleEntriesPreserveOrder() {
        BaiduCensorResponse response = GSON.fromJson(
                "{\"log_id\":100,\"conclusionType\":2,\"data\":["
                        + "{\"type\":1,\"subType\":1,\"msg\":\"m1\",\"probability\":0.1},"
                        + "{\"type\":2,\"subType\":2,\"msg\":\"m2\",\"probability\":0.2}]}",
                BaiduCensorResponse.class);
        AuditResult result = normalizer.normalize(response,
                BaiduAuditResultNormalizer.context(BaiduProviderPlanFixture.textPlan("1"), null, null));

        assertThat(result.hits()).extracting(hit -> hit.labelPath())
                .isEqualTo(List.of("1.1", "2.2"));
    }
}
