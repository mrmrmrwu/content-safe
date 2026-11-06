package io.github.wrqtec.content.safe.samples.basic;

import java.util.List;
import java.util.Map;

import io.github.wrqtec.content.safe.api.AuditRequest;
import io.github.wrqtec.content.safe.api.AuditResult;
import io.github.wrqtec.content.safe.api.RiskHit;
import io.github.wrqtec.content.safe.api.enums.ContentType;
import io.github.wrqtec.content.safe.api.enums.RiskSuggestion;
import io.github.wrqtec.content.safe.model.AuditCapabilities;
import io.github.wrqtec.content.safe.model.ProviderPlan;
import io.github.wrqtec.content.safe.model.SceneDefinition;
import io.github.wrqtec.content.safe.spi.AuditOperation;
import io.github.wrqtec.content.safe.spi.AuditOperationProvider;
import org.springframework.stereotype.Component;

/**
 * Mock 供应商：演示如何实现 SPI
 * 真实供应商（阿里云/腾讯等）在 provider-* 模块中以同样方式接入
 * 规则：文本包含"违禁"判 REJECT，包含"疑似"判 REVIEW（演示人审闭环），其余 PASS；
 * AIGC 开关打开且文本包含"AI生成"时叠加 aigc 命中
 *
 * @author L.David
 */
@Component
public class FakeAuditOperationProvider implements AuditOperationProvider {

    private static final String PROVIDER = "fake";

    /** 演示用违禁关键词：文本包含即判 REJECT */
    private static final String BANNED_KEYWORD = "违禁";

    /** 演示用存疑关键词：文本包含即判 REVIEW */
    private static final String SUSPECT_KEYWORD = "疑似";

    /** 演示用 AIGC 关键词：开关打开且文本包含即命中 */
    private static final String AIGC_KEYWORD = "AI生成";

    /** 演示用命中标签 */
    private static final String RISK_LABEL = "contraband";

    /** 演示用 AIGC 命中标签 */
    private static final String AIGC_LABEL = "aigc";

    /** 演示用命中置信分 */
    private static final double HIT_SCORE = 0.95D;

    private final AuditOperation textScan = new AuditOperation() {
        @Override
        public ProviderPlan plan() {
            return new ProviderPlan(PROVIDER, "fake-moderation", "v1", "textScan", null, null, null, null);
        }

        @Override
        public AuditCapabilities capabilities() {
            return AuditCapabilities.builder().contentTypes(ContentType.TEXT).sync(true).aigcDetect(true).build();
        }

        @Override
        public AuditResult audit(AuditRequest request) {
            String text = request.content().value();
            if (text != null && text.contains(BANNED_KEYWORD)) {
                RiskHit hit = new RiskHit(RISK_LABEL, List.of(RISK_LABEL), HIT_SCORE, null,
                        null, text, null, Map.of());
                return AuditResult.builder()
                        .ok(true)
                        .suggestion(RiskSuggestion.REJECT).dataId(request.dataId())
                        .hits(List.of(hit))
                        .provider(PROVIDER)
                        .build();
            }
            if (text != null && text.contains(SUSPECT_KEYWORD)) {
                return AuditResult.builder()
                        .ok(true).dataId(request.dataId())
                        .suggestion(RiskSuggestion.REVIEW)
                        .provider(PROVIDER)
                        .build();
            }
            AuditResult.Builder builder = AuditResult.builder().ok(true).dataId(request.dataId())
                    .suggestion(RiskSuggestion.PASS).provider(PROVIDER);
            if (text != null && text.contains(AIGC_KEYWORD)
                    && Boolean.TRUE.equals(request.options().enableAigcDetect())) {
                RiskHit aigcHit = new RiskHit(AIGC_LABEL, List.of(AIGC_LABEL), HIT_SCORE, null,
                        null, null, null, Map.of());
                builder.suggestion(RiskSuggestion.REJECT).hits(List.of(aigcHit));
            }
            return builder.build();
        }
    };

    @Override
    public String provider() {
        return PROVIDER;
    }

    @Override
    public List<ContentType> supportedTypes() {
        return List.of(ContentType.TEXT);
    }

    @Override
    public List<AuditOperation> operations(SceneDefinition scene) {
        return List.of(textScan);
    }
}
