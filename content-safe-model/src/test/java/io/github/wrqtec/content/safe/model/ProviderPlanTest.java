package io.github.wrqtec.content.safe.model;

import io.github.wrqtec.content.safe.api.exception.InvalidRequestException;
import io.github.wrqtec.content.safe.model.enums.AuditMode;
import io.github.wrqtec.content.safe.model.enums.ProviderLifecycle;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * {@link ProviderPlan} 单元测试。
 *
 * @author L.David
 */
class ProviderPlanTest {

    @Test
    void defaultsApplied() {
        ProviderPlan plan = new ProviderPlan("aliyun", "green", "1", "text", "s1", null, null, null);
        assertEquals(AuditMode.SYNC, plan.mode());
        assertEquals(ProviderLifecycle.ACTIVE, plan.lifecycle());
        assertEquals("aliyun:text", plan.planKey());
        assertNotNull(plan.limits());
    }

    @Test
    void blankProviderRejected() {
        assertThrows(InvalidRequestException.class,
                () -> new ProviderPlan(" ", "g", "1", "op", "s", null, null, null));
    }
}
