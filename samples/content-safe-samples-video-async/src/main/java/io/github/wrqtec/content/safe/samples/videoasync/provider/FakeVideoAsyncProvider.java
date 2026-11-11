package io.github.wrqtec.content.safe.samples.videoasync.provider;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import io.github.wrqtec.content.safe.api.AuditRequest;
import io.github.wrqtec.content.safe.api.AuditResult;
import io.github.wrqtec.content.safe.api.ContentRef;
import io.github.wrqtec.content.safe.api.RiskHit;
import io.github.wrqtec.content.safe.api.enums.AuditSource;
import io.github.wrqtec.content.safe.api.enums.ConfidenceScale;
import io.github.wrqtec.content.safe.api.enums.ContentRefType;
import io.github.wrqtec.content.safe.api.enums.ContentType;
import io.github.wrqtec.content.safe.api.enums.HitSource;
import io.github.wrqtec.content.safe.api.enums.RiskSuggestion;
import io.github.wrqtec.content.safe.api.exception.UnsupportedContentException;
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
 * 模拟异步视频审核供应商：submitAsync 受理任务，query 按调用次数推进状态机
 * （第 1 次查询返回处理中，第 2 次起返回终态 REJECT 并携带帧/语音命中）。
 *
 * <p>mock 规则：providerTaskId = "fake-video-task-" + dataId。
 * 仅接受 VIDEO + URL 内容（视频审核的真实约束：同步不做、入参只承诺 URL）。
 *
 * @author L.David
 */
@Component
public class FakeVideoAsyncProvider implements AuditOperationProvider {

    public static final String PROVIDER = "fake-video";

    /** providerTaskId 前缀 */
    private static final String TASK_ID_PREFIX = "fake-video-task-";

    /** 终态前需要的处理中查询次数 */
    private static final int PENDING_QUERIES = 1;

    private final Map<String, AtomicInteger> queryCounts = new ConcurrentHashMap<>();

    @Override
    public String provider() {
        return PROVIDER;
    }

    @Override
    public List<ContentType> supportedTypes() {
        return List.of(ContentType.VIDEO);
    }

    @Override
    public List<AuditOperation> operations(SceneDefinition scene) {
        ProviderPlan plan = new ProviderPlan(PROVIDER, "fake-moderation", "v1", "videoAsyncScan",
                null, AuditMode.ASYNC_QUERY, null, null);
        AuditCapabilities capabilities = AuditCapabilities.builder()
                .contentTypes(ContentType.VIDEO)
                .url(true)
                .asyncQuery(true)
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
                throw new UnsupportedFeatureException(
                        PROVIDER + " video moderation is asynchronous only; use submitAsync/query");
            }

            @Override
            public AsyncTaskRef submitAsync(AuditRequest request) {
                requireVideoUrl(request);
                return new AsyncTaskRef(TASK_ID_PREFIX + request.dataId(), request.dataId());
            }

            @Override
            public AuditResult query(AsyncTaskRef ref) {
                int queries = queryCounts
                        .computeIfAbsent(ref.providerTaskId(), key -> new AtomicInteger())
                        .incrementAndGet();
                if (queries <= PENDING_QUERIES) {
                    return inProgress(ref);
                }
                return terminal(ref);
            }
        });
    }

    private void requireVideoUrl(AuditRequest request) {
        if (request.type() != ContentType.VIDEO
                || request.content().type() != ContentRefType.URL) {
            throw new UnsupportedContentException(PROVIDER
                    + " video moderation supports VIDEO url content only, got "
                    + request.type() + "/" + request.content().type());
        }
    }

    /**
     * 处理中：ok=true 但 finalResult=false 且 suggestion 必须显式置 null
     * （AuditResult.builder 默认 suggestion=PASS/finalResult=true，不置空会把
     * 处理中误报成"PASS 终态"）
     */
    private AuditResult inProgress(AsyncTaskRef ref) {
        return AuditResult.builder()
                .ok(true)
                .suggestion(null)
                .finalResult(false)
                .source(AuditSource.ASYNC_QUERY)
                .provider(PROVIDER)
                .dataId(ref.dataId())
                .requestId(ref.providerTaskId())
                .build();
    }

    /** 终态：帧命中 + 语音命中各一条，演示 hit.raw 只放技术元数据（无用户内容） */
    private AuditResult terminal(AsyncTaskRef ref) {
        RiskHit frameHit = new RiskHit("porn/sexy", List.of("porn", "sexy"),
                0.96, ConfidenceScale.ZERO_TO_ONE, HitSource.MACHINE,
                null, null,
                Map.of("offset", 12.5f, "sliceId", "slice-001"));
        RiskHit audioHit = new RiskHit("ad/contact", List.of("ad", "contact"),
                null, null, HitSource.MACHINE,
                null, null,
                Map.of("startTime", 5000, "endTime", 9000));
        return AuditResult.builder()
                .ok(true)
                .suggestion(RiskSuggestion.REJECT)
                .finalResult(true)
                .source(AuditSource.ASYNC_QUERY)
                .provider(PROVIDER)
                .dataId(ref.dataId())
                .requestId(ref.providerTaskId())
                .hits(List.of(frameHit, audioHit))
                .build();
    }
}
