package io.github.wrqtec.content.safe.provider.shumei.internal;

import java.time.Duration;

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
 * 数美视频文件检测操作（提交 /video/v4 + 查询 /video/query/v4 + 回调）
 *
 * <p>提交响应仅回执（无 taskId），任务关联键即请求体 {@code data.btId}（取业务 dataId，
 * 缺省时按 tokenId 同款链路生成）；查询按 {@code btId} 检索，1101=处理中为中间态。
 * 仅支持公网 URL 内容（视频入参只承诺 URL）。截帧方式官方必传，经 extra 的
 * {@code screenshot-type} 配置（默认 0 固定间隔 5s）；detectFrequency/checkFrameCount/
 * returnAllImg/returnAllAudio 等可选截帧参数经场景 ext 透传；回调地址经场景 ext 的
 * {@code callback} 字段透传（官方提交字段）。
 *
 * <p>回调报文与查询响应同构且官方无签名机制，由 {@link ShumeiCallbackVerifier} 做结构校验；
 * 本操作按报文 {@code btId} 关联任务。
 *
 * @author L.David
 */
@Slf4j
public class ShumeiVideoAsyncOperation implements AuditOperation {

    private static final Gson GSON = new Gson();

    private final ProviderPlan plan;
    private final ShumeiClientInvoker client;
    private final ShumeiAuditResultNormalizer normalizer;
    private final String imgType;
    private final String audioType;
    private final int screenshotType;
    private final SceneDefinition scene;
    private final AuditCapabilities capabilities;

    public ShumeiVideoAsyncOperation(ProviderPlan plan, ShumeiClientInvoker client,
                                     ShumeiAuditResultNormalizer normalizer, String imgType,
                                     String audioType, int screenshotType, SceneDefinition scene) {
        this.plan = Asserts.notNull(plan, "plan");
        this.client = Asserts.notNull(client, "client");
        this.normalizer = Asserts.notNull(normalizer, "normalizer");
        this.imgType = imgType;
        this.audioType = audioType;
        this.screenshotType = screenshotType;
        this.scene = scene;
        // 回调接收地址由数美按提交时 data.callback 推送（不经过本库），回调通道恒可用
        this.capabilities = AuditCapabilities.builder()
                .contentTypes(ContentType.VIDEO)
                .url(true)
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
                "shumei video operation does not support sync audit; use submitAsync/query");
    }

    @Override
    public AsyncTaskRef submitAsync(AuditRequest request) {
        Asserts.notNull(request, "request");
        if (!capabilities.supports(request)) {
            throw new UnsupportedContentException("shumei video supports VIDEO url content only, got request type "
                    + request.type() + "/" + request.content().type());
        }
        // btId（官方必传的客户侧请求唯一标识）= 任务关联键，取业务 dataId，缺省生成随机标识
        // （不用 tokenIdOf：userId 对同一用户不唯一，会撞 btId）
        String btId = request.dataId() == null || request.dataId().isBlank()
                ? java.util.UUID.randomUUID().toString().replace("-", "") : request.dataId();
        try {
            client.videoSubmit(plan.service(), imgType, audioType,
                    ShumeiPayloads.videoData(request.content().value(),
                            ShumeiPayloads.tokenIdOf(request), btId,
                            ShumeiTextDetectOperation.ipOf(request), screenshotType, scene));
            return new AsyncTaskRef(btId, btId);
        } catch (ContentSafeException e) {
            throw e;
        } catch (Exception e) {
            throw ShumeiErrorMapper.fromException(e, "video submit");
        }
    }

    @Override
    public AuditResult query(AsyncTaskRef ref) {
        Asserts.notNull(ref, "ref");
        Asserts.notBlank(ref.providerTaskId(), "ref.providerTaskId");
        try {
            ShumeiVideoQueryResponse response = client.videoQuery(ref.providerTaskId());
            if (response.isProcessing()) {
                return inProgress(ref);
            }
            if (!response.isSuccess()) {
                throw new ProviderCallException("shumei video query rejected: code="
                        + response.getCode() + " message="
                        + ShumeiErrorMapper.sanitize(response.getMessage()));
            }
            return normalizer.normalizeVideoQuery(response,
                    ShumeiAuditResultNormalizer.context(plan, ref.dataId(), Duration.ZERO));
        } catch (ContentSafeException e) {
            throw e;
        } catch (Exception e) {
            throw ShumeiErrorMapper.fromException(e, "video query");
        }
    }

    @Override
    public CallbackResult handleCallback(CallbackRequest request) {
        Asserts.notNull(request, "request");
        ShumeiVideoQueryResponse response;
        try {
            response = GSON.fromJson(request.rawBody(), ShumeiVideoQueryResponse.class);
        } catch (JsonSyntaxException e) {
            response = null;
        }
        if (response == null) {
            log.warn("shumei video callback ignored: invalid payload");
            return CallbackResult.rejected("invalid shumei video callback payload");
        }
        if (!response.isSuccess()) {
            log.warn("shumei video callback rejected: code={}", response.getCode());
            return CallbackResult.rejected("shumei video callback not successful, code="
                    + response.getCode());
        }
        if (response.getBtId() == null || response.getBtId().isBlank()) {
            log.warn("shumei video callback rejected: missing btId (task reference)");
            return CallbackResult.rejected("shumei video callback missing btId (task reference)");
        }
        AuditResult result = normalizer.normalizeVideoCallback(response,
                ShumeiAuditResultNormalizer.context(plan, response.getBtId(), Duration.ZERO));
        return CallbackResult.accepted(response.getBtId(), result);
    }

    /**
     * 处理中中间态：查询返回 1101（视频处理中）
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
                .hits(java.util.List.of())
                .build();
    }
}
