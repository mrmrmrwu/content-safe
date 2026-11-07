package io.github.wrqtec.content.safe.provider.aliyun.internal;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

import com.aliyun.green20220302.models.DescribeImageModerationResultResponseBody;
import com.aliyun.green20220302.models.ImageAsyncModerationResponseBody;
import io.github.wrqtec.content.safe.api.AuditRequest;
import io.github.wrqtec.content.safe.api.AuditResult;
import io.github.wrqtec.content.safe.api.enums.AuditSource;
import io.github.wrqtec.content.safe.api.enums.ContentType;
import io.github.wrqtec.content.safe.api.enums.RiskSuggestion;
import io.github.wrqtec.content.safe.api.exception.ContentSafeException;
import io.github.wrqtec.content.safe.api.exception.ProviderCallException;
import io.github.wrqtec.content.safe.api.exception.UnsupportedContentException;
import io.github.wrqtec.content.safe.api.exception.UnsupportedFeatureException;
import io.github.wrqtec.content.safe.api.util.Asserts;
import io.github.wrqtec.content.safe.model.AuditCapabilities;
import io.github.wrqtec.content.safe.model.AsyncTaskRef;
import io.github.wrqtec.content.safe.model.ProviderPlan;
import io.github.wrqtec.content.safe.provider.aliyun.AliyunProviderConstants;
import io.github.wrqtec.content.safe.spi.AuditOperation;
import lombok.extern.slf4j.Slf4j;

/**
 * 阿里云图片异步审核（ImageAsyncModeration 提交 + DescribeImageModerationResult 轮询）。
 * 异步仅接受公网可达 URL（imageUrl / OSS），base64/bytes 直接拒绝（铁律 §5.2 能力先行）；
 * 配置 extra callback-url 后提交携带回调地址并声明 asyncCallback 能力位。
 * 官方约束（help.aliyun.com/doc-detail/2638108，查阅 2026-11-07）：异步检测仅
 * green-cip cn-shanghai / cn-beijing 两地域支持，结果保留 3 天，建议 30 秒间隔轮询
 *
 * @author L.David
 */
@Slf4j
public class AliyunImageAsyncOperation implements AuditOperation {

    /** serviceParameters 键（callback/seed 语义见 AliyunProviderConstants.EXTRA_CALLBACK_*） */
    static final String PARAM_IMAGE_URL = "imageUrl";
    static final String PARAM_DATA_ID = "dataId";
    static final String PARAM_CALLBACK = "callback";
    static final String PARAM_SEED = "seed";

    /** 查询业务码：任务检测中（官方 code=280） */
    static final int CODE_DETECTING = 280;

    private final ProviderPlan plan;
    private final AuditCapabilities capabilities;
    private final GreenClientInvoker invoker;
    private final AliyunAuditResultNormalizer normalizer;
    private final String callbackUrl;
    private final String callbackSeed;

    public AliyunImageAsyncOperation(ProviderPlan plan, GreenClientInvoker invoker,
                                     AliyunAuditResultNormalizer normalizer,
                                     String callbackUrl, String callbackSeed) {
        this.plan = Asserts.notNull(plan, "plan");
        this.invoker = Asserts.notNull(invoker, "invoker");
        this.normalizer = Asserts.notNull(normalizer, "normalizer");
        this.callbackUrl = callbackUrl;
        this.callbackSeed = callbackSeed;
        this.capabilities = AuditCapabilities.builder()
                .contentTypes(ContentType.IMAGE)
                .url(true)
                .asyncQuery(true)
                .asyncCallback(callbackUrl != null && !callbackUrl.isBlank())
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
        throw new UnsupportedFeatureException("aliyun image async operation does not support synchronous audit; "
                + "use submitAsync/query");
    }

    @Override
    public AsyncTaskRef submitAsync(AuditRequest request) {
        Asserts.notNull(request, "request");
        if (!capabilities.supports(request)) {
            throw new UnsupportedContentException("aliyun image async moderation only supports public url, "
                    + "got content type " + request.type() + "/" + request.content().type());
        }
        Map<String, String> serviceParameters = new LinkedHashMap<>();
        serviceParameters.put(PARAM_IMAGE_URL, request.content().value());
        if (request.dataId() != null) {
            serviceParameters.put(PARAM_DATA_ID, request.dataId());
        }
        if (callbackUrl != null && !callbackUrl.isBlank()) {
            serviceParameters.put(PARAM_CALLBACK, callbackUrl);
            if (callbackSeed != null && !callbackSeed.isBlank()) {
                serviceParameters.put(PARAM_SEED, callbackSeed);
            }
        }
        long startNanos = System.nanoTime();
        try {
            ImageAsyncModerationResponseBody body = invoker.imageAsyncModeration(plan.service(), serviceParameters);
            AliyunErrorMapper.assertSuccess(body.getCode(), body.getMsg());
            String reqId = reqIdOf(body);
            log.debug("aliyun image async submitted planKey={} requestId={}", plan.planKey(), body.getRequestId());
            return new AsyncTaskRef(reqId, dataIdOf(body, request.dataId()));
        } catch (ContentSafeException e) {
            throw e;
        } catch (Exception e) {
            throw AliyunErrorMapper.fromException(e);
        }
    }

    @Override
    public AuditResult query(AsyncTaskRef ref) {
        Asserts.notNull(ref, "ref");
        Asserts.notBlank(ref.providerTaskId(), "ref.providerTaskId");
        long startNanos = System.nanoTime();
        try {
            var body = invoker.describeImageModerationResult(ref.providerTaskId());
            Integer code = body.getCode();
            if (code != null && code == CODE_DETECTING) {
                // 检测中：产出中间态结果（suggestion 置空，finalResult=false 由 core 继续轮询）
                return inProgress(body, ref);
            }
            AliyunErrorMapper.assertSuccess(code, body.getMsg());
            Duration cost = Duration.ofNanos(System.nanoTime() - startNanos);
            return normalizer.normalizeImageAsyncResult(body,
                    AliyunAuditResultNormalizer.context(plan, ref.dataId(), cost));
        } catch (ContentSafeException e) {
            throw e;
        } catch (Exception e) {
            throw AliyunErrorMapper.fromException(e);
        }
    }

    /** 任务引用：data.reqId 优先，缺省回退顶层 requestId（两者均为提交时返回的请求 ID） */
    private static String reqIdOf(ImageAsyncModerationResponseBody body) {
        String reqId = body.getData() == null ? null : body.getData().getReqId();
        if (reqId == null || reqId.isBlank()) {
            reqId = body.getRequestId();
        }
        if (reqId == null || reqId.isBlank()) {
            throw new ProviderCallException("aliyun image async submit returned no reqId");
        }
        return reqId;
    }

    private static String dataIdOf(ImageAsyncModerationResponseBody body, String fallback) {
        return body.getData() != null && body.getData().getDataId() != null
                ? body.getData().getDataId() : fallback;
    }

    private AuditResult inProgress(DescribeImageModerationResultResponseBody body, AsyncTaskRef ref) {
        Map<String, Object> raw = new LinkedHashMap<>();
        raw.put("code", CODE_DETECTING);
        raw.put("requestId", body.getRequestId());
        return AuditResult.builder()
                .ok(true)
                .suggestion(null)
                .finalResult(false)
                .source(AuditSource.SYNC)
                .provider(AliyunProviderConstants.PROVIDER)
                .product(AliyunProviderConstants.PRODUCT)
                .service(plan.service())
                .dataId(ref.dataId())
                .requestId(body.getRequestId())
                .raw(raw)
                .build();
    }
}
