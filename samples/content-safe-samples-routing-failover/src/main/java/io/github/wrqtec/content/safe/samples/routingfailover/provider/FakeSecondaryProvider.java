package io.github.wrqtec.content.safe.samples.routingfailover.provider;

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
 * 模拟备用供应商：优先级次于主供应商（order=10），正常工作。
 * 文本包含"违禁"判 REJECT，否则 PASS。
 *
 * @author L.David
 */
@Component
public class FakeSecondaryProvider implements AuditOperationProvider {

    public static final String PROVIDER = "fake-secondary";

    /** 触发 REJECT 的模拟违禁关键词 */
    private static final String BANNED_KEYWORD = "违禁";

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
        ProviderPlan plan = new ProviderPlan(PROVIDER, "fake-moderation", "v1", "textScan",
                null, null, null, null);
        AuditCapabilities capabilities = AuditCapabilities.builder()
                .contentTypes(ContentType.TEXT)
                .sync(true)
                .build();
        return List.of(new AuditOperation() {
            @Override
            public ProviderPlan plan() {
                return plan;
            }

            @Override
            public AuditCapabilities capabilities() {
                return capabilities;
            }

            @Override
            public int order() {
                return 10;
            }

            @Override
            public AuditResult audit(AuditRequest request) {
                String text = request.content().value();
                if (text != null && text.contains(BANNED_KEYWORD)) {
                    String label = "banned_word";
                    RiskHit hit = new RiskHit(label, List.of(label), 0.95D, null, null, BANNED_KEYWORD, null, Map.of());
                    return AuditResult.builder()
                            .ok(true)
                            .suggestion(RiskSuggestion.REJECT)
                            .hits(List.of(hit))
                            .provider(PROVIDER)
                            .build();
                }
                return AuditResult.builder()
                        .ok(true)
                        .suggestion(RiskSuggestion.PASS)
                        .provider(PROVIDER)
                        .build();
            }
        });
    }
}
