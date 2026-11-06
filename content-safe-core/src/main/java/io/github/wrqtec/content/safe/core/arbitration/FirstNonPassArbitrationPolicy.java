package io.github.wrqtec.content.safe.core.arbitration;

import java.util.Comparator;
import java.util.List;

import io.github.wrqtec.content.safe.api.AuditResult;
import io.github.wrqtec.content.safe.api.enums.RiskSuggestion;
import io.github.wrqtec.content.safe.api.util.Asserts;
import io.github.wrqtec.content.safe.spi.AuditArbitrationPolicy;

/**
 * 从严仲裁：任一供应商结果非 PASS 即采用最严重的那个（REJECT &gt; REVIEW &gt; PASS）。
 *
 * @author L.David
 */
public class FirstNonPassArbitrationPolicy implements AuditArbitrationPolicy {

    /** 策略名。 */
    private static final String NAME = "FIRST_NON_PASS";

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public AuditResult arbitrate(List<AuditResult> results) {
        Asserts.notNull(results, "results");
        if (results.isEmpty()) {
            throw new IllegalArgumentException("results must not be empty");
        }
        return results.stream()
                .max(Comparator.comparingInt(r -> severity(r.suggestion())))
                .orElseThrow();
    }

    private static int severity(RiskSuggestion suggestion) {
        if (suggestion == null) {
            return 0;
        }
        return switch (suggestion) {
            case PASS -> 0;
            case ERROR -> 1;
            case REVIEW -> 2;
            case REJECT -> 3;
        };
    }
}
