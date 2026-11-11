package io.github.wrqtec.content.safe.provider.shumei.internal;

import java.time.Duration;
import java.util.List;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import io.github.wrqtec.content.safe.api.AuditRequest;
import io.github.wrqtec.content.safe.api.AuditResult;
import io.github.wrqtec.content.safe.api.CallbackRequest;
import io.github.wrqtec.content.safe.api.CallbackResult;
import io.github.wrqtec.content.safe.api.enums.AuditSource;
import io.github.wrqtec.content.safe.api.enums.ContentType;
import io.github.wrqtec.content.safe.api.exception.ContentSafeException;
import io.github.wrqtec.content.safe.api.exception.ProviderCallException;
import io.github.wrqtec.content.safe.api.exception.UnsupportedContentException;
import io.github.wrqtec.content.safe.api.exception.UnsupportedFeatureException;
import io.github.wrqtec.content.safe.api.util.Asserts;
import io.github.wrqtec.content.safe.model.AsyncTaskRef;
import io.github.wrqtec.content.safe.model.AuditCapabilities;
import io.github.wrqtec.content.safe.model.ProviderPlan;
import io.github.wrqtec.content.safe.model.SceneDefinition;
import io.github.wrqtec.content.safe.spi.AuditOperation;
import lombok.extern.slf4j.Slf4j;

/**
 * 数美图片异步检测操作（提交 /v4/saas/async/img + 查询 /v4/image/query + 回调）
 *
 * <p>请求体与同步 image/v4 同构（img 支持 URL / base64 / bytes 三通道）；
 * 提交响应 {@code requestId} 即任务引用；查询按单项 contents 归一（缺失视为检测中，
 * {@code finalResult=0} 为数美人审回传前的非终态）。
 *
 * <p>回调报文与同步响应同构且官方无签名机制（防伪靠回调接收侧 IP 白名单等业务手段），
 * 由 {@link ShumeiCallbackVerifier} 做结构校验；本操作按报文 {@code requestId} 关联任务。
 *
 * @author L.David
 */
@Slf4j
public class ShumeiImageAsyncOperation implements AuditOperation {

    private static final Gson GSON = new Gson();

    private final ProviderPlan plan;
    private final ShumeiClientInvoker client;
    private final ShumeiAuditResultNormalizer normalizer;
    private final String type;
    private final SceneDefinition scene;
    private final AuditCapabilities capabilities;

    public ShumeiImageAsyncOperation(ProviderPlan plan, ShumeiClientInvoker client,
                                     ShumeiAuditResultNormalizer normalizer, String type,
                                     SceneDefinition scene) {
        this.plan = Asserts.notNull(plan, "plan");
        this.client = Asserts.notNull(client, "client");
        this.normalizer = Asserts.notNull(normalizer, "normalizer");
        this.type = Asserts.notBlank(type, "type");
        this.scene = scene;
        // 回调接收地址在数美控制台配置（不经过本库），回调通道恒可用
        this.capabilities = AuditCapabilities.builder()
                .contentTypes(ContentType.IMAGE)
                .url(true)
                .base64(true)
                .bytes(true)
                .asyncQuery(true)
                .asyncCallback(true)
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
                "shumei image async operation does not support sync audit");
    }

    @Override
    public AsyncTaskRef submitAsync(AuditRequest request) {
        Asserts.notNull(request, "request");
        if (!capabilities.supports(request)) {
            throw new UnsupportedContentException("shumei image async only supports IMAGE content");
        }
        String tokenId = ShumeiPayloads.tokenIdOf(request);
        try {
            ShumeiSyncResponse response = client.imageAsyncSubmit(plan.service(), type,
                    ShumeiPayloads.imageData(request.content(), tokenId, request.dataId(),
                            ShumeiTextDetectOperation.ipOf(request), scene));
            String requestId = response.getRequestId();
            if (requestId == null || requestId.isBlank()) {
                throw new ProviderCallException("shumei image async submit returned no requestId");
            }
            return new AsyncTaskRef(requestId, request.dataId());
        } catch (ContentSafeException e) {
            throw e;
        } catch (Exception e) {
            throw ShumeiErrorMapper.fromException(e, "async submit");
        }
    }

    @Override
    public AuditResult query(AsyncTaskRef ref) {
        Asserts.notNull(ref, "ref");
        Asserts.notBlank(ref.providerTaskId(), "ref.providerTaskId");
        long startNanos = System.nanoTime();
        try {
            ShumeiAsyncQueryResponse response = client.imageAsyncQuery(List.of(ref.providerTaskId()));
            List<ShumeiSyncResponse> contents = response.getContents();
            if (contents == null || contents.isEmpty() || contents.get(0) == null) {
                return inProgress(ref);
            }
            Duration cost = Duration.ofNanos(System.nanoTime() - startNanos);
            return normalizer.normalizeQuery(contents.get(0),
                    ShumeiAuditResultNormalizer.context(plan, ref.dataId(), cost));
        } catch (ContentSafeException e) {
            throw e;
        } catch (Exception e) {
            throw ShumeiErrorMapper.fromException(e, "async query");
        }
    }

    @Override
    public CallbackResult handleCallback(CallbackRequest request) {
        Asserts.notNull(request, "request");
        ShumeiSyncResponse response;
        try {
            response = GSON.fromJson(request.rawBody(), ShumeiSyncResponse.class);
        } catch (JsonSyntaxException e) {
            response = null;
        }
        if (response == null) {
            log.warn("shumei callback ignored: invalid payload");
            return CallbackResult.rejected("invalid shumei callback payload");
        }
        if (!response.isSuccess()) {
            log.warn("shumei callback rejected: code={}", response.getCode());
            return CallbackResult.rejected("shumei callback not successful, code=" + response.getCode());
        }
        if (response.getRequestId() == null || response.getRequestId().isBlank()) {
            log.warn("shumei callback rejected: missing requestId (task reference)");
            return CallbackResult.rejected("shumei callback missing requestId (task reference)");
        }
        AuditResult result = normalizer.normalizeCallback(response,
                ShumeiAuditResultNormalizer.context(plan, null, Duration.ZERO));
        return CallbackResult.accepted(response.getRequestId(), result);
    }

    /**
     * 检测中中间态：查询成功但 contents 为空（结果未生成）
     */
    private AuditResult inProgress(AsyncTaskRef ref) {
        return AuditResult.builder()
                .ok(true)
                .suggestion(null)
                .finalResult(false)
                .source(AuditSource.ASYNC_QUERY)
                .provider(plan.provider())
                .product(plan.product())
                .service(plan.service())
                .dataId(ref.dataId())
                .requestId(ref.providerTaskId())
                .hits(List.of())
                .build();
    }
}
