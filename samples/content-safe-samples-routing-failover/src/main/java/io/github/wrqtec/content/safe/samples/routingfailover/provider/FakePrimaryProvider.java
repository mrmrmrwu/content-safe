package io.github.wrqtec.content.safe.samples.routingfailover.provider;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import io.github.wrqtec.content.safe.api.AuditRequest;
import io.github.wrqtec.content.safe.api.AuditResult;
import io.github.wrqtec.content.safe.api.enums.ContentType;
import io.github.wrqtec.content.safe.api.exception.ProviderCallException;
import io.github.wrqtec.content.safe.model.AuditCapabilities;
import io.github.wrqtec.content.safe.model.ProviderPlan;
import io.github.wrqtec.content.safe.model.SceneDefinition;
import io.github.wrqtec.content.safe.spi.AuditOperation;
import io.github.wrqtec.content.safe.spi.AuditOperationProvider;
import org.springframework.stereotype.Component;

/**
 * 模拟主供应商：优先级最高（order=0）但调用恒失败，用于演示自动 failover。
 *
 * @author L.David
 */
@Component
public class FakePrimaryProvider implements AuditOperationProvider {

    public static final String PROVIDER = "fake-primary";

    /** 被调用次数，供测试断言主供应商确实被尝试过 */
    private final AtomicInteger calls = new AtomicInteger();

    @Override
    public String provider() {
        return PROVIDER;
    }

    @Override
    public List<ContentType> supportedTypes() {
        return List.of(ContentType.TEXT);
    }

    public int calls() {
        return calls.get();
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
                // 数值最小，路由时最先被选中
                return 0;
            }

            @Override
            public AuditResult audit(AuditRequest request) {
                calls.incrementAndGet();
                throw new ProviderCallException("simulated primary outage");
            }
        });
    }
}
