package io.github.wrqtec.content.safe.samples.asynccallback.provider;

import java.util.List;
import java.util.Map;

import io.github.wrqtec.content.safe.api.AuditRequest;
import io.github.wrqtec.content.safe.api.AuditResult;
import io.github.wrqtec.content.safe.api.CallbackRequest;
import io.github.wrqtec.content.safe.api.CallbackResult;
import io.github.wrqtec.content.safe.api.enums.AuditSource;
import io.github.wrqtec.content.safe.api.enums.ContentType;
import io.github.wrqtec.content.safe.api.enums.RiskSuggestion;
import io.github.wrqtec.content.safe.api.exception.UnsupportedFeatureException;
import io.github.wrqtec.content.safe.model.AsyncTaskRef;
import io.github.wrqtec.content.safe.model.AuditCapabilities;
import io.github.wrqtec.content.safe.model.ProviderPlan;
import io.github.wrqtec.content.safe.model.SceneDefinition;
import io.github.wrqtec.content.safe.model.enums.AuditMode;
import io.github.wrqtec.content.safe.spi.AuditOperation;
import io.github.wrqtec.content.safe.spi.AuditOperationProvider;
import org.springframework.stereotype.Component;

/**
 * 模拟异步图片审核供应商：submitAsync 受理任务，终态结果通过回调推送。
 *
 * <p>mock 规则：providerTaskId = "fake-task-" + dataId；
 * 回调体格式 {@code taskId=<providerTaskId>&suggestion=<PASS|REJECT>}。
 *
 * @author L.David
 */
@Component
public class FakeAsyncProvider implements AuditOperationProvider {

    public static final String PROVIDER = "fake-async";

    /** providerTaskId 前缀 */
    private static final String TASK_ID_PREFIX = "fake-task-";

    @Override
    public String provider() {
        return PROVIDER;
    }

    @Override
    public List<ContentType> supportedTypes() {
        return List.of(ContentType.IMAGE);
    }

    @Override
    public List<AuditOperation> operations(SceneDefinition scene) {
        ProviderPlan plan = new ProviderPlan(PROVIDER, "fake-moderation", "v1", "imageAsyncScan",
                null, AuditMode.ASYNC_CALLBACK, null, null);
        AuditCapabilities capabilities = AuditCapabilities.builder()
                .contentTypes(ContentType.IMAGE)
                .url(true)
                .asyncQuery(true)
                .asyncCallback(true)
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
                return 0;
            }

            @Override
            public AuditResult audit(AuditRequest request) {
                throw new UnsupportedFeatureException(PROVIDER + " supports async image audit only");
            }

            @Override
            public AsyncTaskRef submitAsync(AuditRequest request) {
                // 确定性映射：便于演示方预知 providerTaskId 伪造回调
                return new AsyncTaskRef(TASK_ID_PREFIX + request.dataId(), request.dataId());
            }

            @Override
            public AuditResult query(AsyncTaskRef ref) {
                // 中间态：finalResult=false，任务保持 PENDING，终态以回调为准
                return AuditResult.builder()
                        .ok(true)
                        .suggestion(RiskSuggestion.PASS)
                        .finalResult(false)
                        .source(AuditSource.ASYNC_QUERY)
                        .provider(PROVIDER)
                        .build();
            }

            @Override
            public CallbackResult handleCallback(CallbackRequest request) {
                Map<String, String> params = parseForm(request.rawBody());
                String taskId = params.get("taskId");
                if (taskId == null || taskId.isBlank()) {
                    return CallbackResult.rejected("missing taskId");
                }
                RiskSuggestion suggestion = RiskSuggestion.PASS;
                String raw = params.get("suggestion");
                if (raw != null) {
                    try {
                        suggestion = RiskSuggestion.valueOf(raw);
                    } catch (IllegalArgumentException e) {
                        return CallbackResult.rejected("unknown suggestion: " + raw);
                    }
                }
                AuditResult result = AuditResult.builder()
                        .ok(true)
                        .suggestion(suggestion)
                        .source(AuditSource.ASYNC_CALLBACK)
                        .provider(PROVIDER)
                        .build();
                return CallbackResult.accepted(taskId, result);
            }

            private Map<String, String> parseForm(String rawBody) {
                Map<String, String> params = new java.util.HashMap<>();
                if (rawBody == null || rawBody.isBlank()) {
                    return params;
                }
                for (String pair : rawBody.split("&")) {
                    int idx = pair.indexOf('=');
                    if (idx > 0) {
                        params.put(pair.substring(0, idx), pair.substring(idx + 1));
                    }
                }
                return params;
            }
        });
    }
}
