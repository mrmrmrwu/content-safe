package io.github.wrqtec.content.safe.provider.volcengine.internal;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import com.volcengine.model.request.ImageRiskResultRequest;
import com.volcengine.model.request.RiskDetectionRequest;
import com.volcengine.model.response.AsyncRiskDetectionResponse;
import com.volcengine.model.response.ResponseMetadata;
import com.volcengine.model.response.VideoRiskResultResponse;
import io.github.wrqtec.content.safe.model.AsyncTaskRef;
import io.github.wrqtec.content.safe.api.AuditRequest;
import io.github.wrqtec.content.safe.api.AuditResult;
import io.github.wrqtec.content.safe.api.enums.AuditSource;
import io.github.wrqtec.content.safe.api.enums.ContentType;
import io.github.wrqtec.content.safe.api.exception.ContentSafeException;
import io.github.wrqtec.content.safe.api.exception.ProviderCallException;
import io.github.wrqtec.content.safe.api.exception.UnsupportedContentException;
import io.github.wrqtec.content.safe.api.exception.UnsupportedFeatureException;
import io.github.wrqtec.content.safe.api.util.Asserts;
import io.github.wrqtec.content.safe.model.AuditCapabilities;
import io.github.wrqtec.content.safe.model.ProviderPlan;
import io.github.wrqtec.content.safe.model.SceneDefinition;
import io.github.wrqtec.content.safe.spi.AuditOperation;
import lombok.extern.slf4j.Slf4j;

/**
 * 火山引擎异步视频检测（AsyncVideoRisk 提交 + VideoResult 查询）。
 * 官方提交回执无任务句柄，查询按 dataId 关联，故任务关联键 = 业务 dataId
 * （缺省时生成 32 位无杠 UUID），并随 parameters 内层 JSON 提交（键名经
 * {@code extra.parameters-video-data-id-key} 配置，缺省 dataId）；视频 URL
 * 写入 {@code extra.parameters-video-key} 指定的键。仅承诺 URL 内容
 * （docs/video-detection-design.md 决策）；中间态语义官方未公开，
 * 按"查询无结论数据（Data 缺失）即处理中"保守实现，超时由 core
 * poll-timeout 兜底（详见 docs/video-detection-design.md 实现勘误）
 *
 * @author L.David
 */
@Slf4j
public class VolcengineVideoAsyncOperation implements AuditOperation {

    private final ProviderPlan plan;
    private final Integer appId;
    private final SceneDefinition scene;
    private final String contentKey;
    private final String dataIdKey;
    private final AuditCapabilities capabilities;
    private final VolcengineClientInvoker invoker;
    private final VolcengineAuditResultNormalizer normalizer;

    public VolcengineVideoAsyncOperation(ProviderPlan plan, Integer appId, SceneDefinition scene,
                                         String contentKey, String dataIdKey,
                                         VolcengineClientInvoker invoker,
                                         VolcengineAuditResultNormalizer normalizer) {
        this.plan = Asserts.notNull(plan, "plan");
        this.appId = Asserts.notNull(appId, "appId");
        this.scene = scene;
        this.contentKey = Asserts.notBlank(contentKey, "contentKey");
        this.dataIdKey = Asserts.notBlank(dataIdKey, "dataIdKey");
        this.invoker = Asserts.notNull(invoker, "invoker");
        this.normalizer = Asserts.notNull(normalizer, "normalizer");
        this.capabilities = AuditCapabilities.builder()
                .contentTypes(ContentType.VIDEO)
                .url(true)
                .asyncQuery(true)
                .build();
    }

    @Override
    public ProviderPlan plan() {
        return plan;
    }

    @Override
    public AuditCapabilities capabilities() {
        return capabilities;
    }

    @Override
    public AuditResult audit(AuditRequest request) {
        throw new UnsupportedFeatureException(
                "volcengine video operation does not support synchronous audit; use submitAsync/query");
    }

    @Override
    public AsyncTaskRef submitAsync(AuditRequest request) {
        Asserts.notNull(request, "request");
        if (!capabilities.supports(request)) {
            throw new UnsupportedContentException("volcengine video supports VIDEO url content only, got "
                    + request.type() + "/" + request.content().type());
        }
        String taskKey = request.dataId() == null || request.dataId().isBlank()
                ? UUID.randomUUID().toString().replace("-", "")
                : request.dataId();
        RiskDetectionRequest sdkRequest = VolcengineParameters.envelope(
                appId, plan.service(), Map.of(dataIdKey, taskKey),
                contentKey, request.content().value(), scene);
        try {
            AsyncRiskDetectionResponse response = invoker.asyncVideoRisk(sdkRequest);
            assertNoMetadataError(response);
            if (response == null || response.getResult() == null) {
                throw new ProviderCallException("volcengine video submit returned empty result");
            }
            log.debug("volcengine video submit done planKey={} taskKey={}", plan.planKey(), taskKey);
            return new AsyncTaskRef(taskKey, request.dataId());
        } catch (ContentSafeException e) {
            throw e;
        } catch (Exception e) {
            throw VolcengineErrorMapper.fromException(e);
        }
    }

    @Override
    public AuditResult query(AsyncTaskRef ref) {
        Asserts.notNull(ref, "ref");
        Asserts.notBlank(ref.providerTaskId(), "ref.providerTaskId");
        ImageRiskResultRequest sdkRequest = new ImageRiskResultRequest();
        sdkRequest.setAppId(appId);
        sdkRequest.setService(plan.service());
        sdkRequest.setDataId(ref.providerTaskId());
        long startNanos = System.nanoTime();
        try {
            VideoRiskResultResponse response = invoker.videoResult(sdkRequest);
            Duration cost = Duration.ofNanos(System.nanoTime() - startNanos);
            assertNoMetadataError(response);
            VideoRiskResultResponse.VideoDetectionResult result =
                    response == null ? null : response.getResult();
            if (result == null) {
                throw new ProviderCallException("volcengine video result returned empty result: dataId="
                        + ref.providerTaskId());
            }
            if (result.getData() == null) {
                // 官方未公开中间态语义：无结论数据即视为处理中，超时由 core poll-timeout 兜底
                log.debug("volcengine video result not ready planKey={} dataId={}", plan.planKey(),
                        ref.providerTaskId());
                return inProgress(ref, result.getRequestId());
            }
            return normalizer.normalizeVideoResult(response,
                    VolcengineAuditResultNormalizer.context(plan, ref.dataId(), cost));
        } catch (ContentSafeException e) {
            throw e;
        } catch (Exception e) {
            throw VolcengineErrorMapper.fromException(e);
        }
    }

    /** 提交回执只含 requestId/passThrough，无终态数据，构造中间态结果 */
    private AuditResult inProgress(AsyncTaskRef ref, String requestId) {
        Map<String, Object> raw = new LinkedHashMap<>();
        if (requestId != null) {
            raw.put("RequestId", requestId);
        }
        return AuditResult.builder()
                .ok(true)
                .suggestion(null)
                .finalResult(false)
                .source(AuditSource.ASYNC_QUERY)
                .provider(plan.provider())
                .product(plan.product())
                .service(plan.service())
                .dataId(ref.dataId())
                .requestId(requestId)
                .raw(raw)
                .build();
    }

    /** 网关/鉴权层元数据错误统一抛调用失败 */
    private static void assertNoMetadataError(AsyncRiskDetectionResponse response) {
        if (response == null) {
            return;
        }
        ResponseMetadata metadata = response.getResponseMetadata();
        if (metadata == null || metadata.getError() == null) {
            return;
        }
        throw VolcengineErrorMapper.fromResponseError(metadata.getError(), metadata.getRequestId());
    }

    private static void assertNoMetadataError(VideoRiskResultResponse response) {
        if (response == null) {
            return;
        }
        ResponseMetadata metadata = response.getResponseMetadata();
        if (metadata == null || metadata.getError() == null) {
            return;
        }
        throw VolcengineErrorMapper.fromResponseError(metadata.getError(), metadata.getRequestId());
    }
}
