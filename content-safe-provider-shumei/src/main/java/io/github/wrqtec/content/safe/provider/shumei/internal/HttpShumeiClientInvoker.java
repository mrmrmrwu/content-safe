package io.github.wrqtec.content.safe.provider.shumei.internal;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import io.github.wrqtec.content.safe.api.exception.ConfigInvalidException;
import io.github.wrqtec.content.safe.api.exception.InvalidRequestException;
import io.github.wrqtec.content.safe.api.exception.ProviderCallException;
import io.github.wrqtec.content.safe.api.util.Asserts;
import io.github.wrqtec.content.safe.provider.shumei.ShumeiProviderConstants;
import io.github.wrqtec.content.safe.provider.shumei.ShumeiProviderProperties;

/**
 * 数美检测 HTTP 客户端（JDK HttpClient + Gson）
 *
 * <p>POST JSON 请求体：{accessKey, appId, eventId, type, data}；
 * accessKey 仅在本类装配进请求体，不进入日志与异常。
 *
 * @author L.David
 */
public class HttpShumeiClientInvoker implements ShumeiClientInvoker {

    private static final Gson GSON = new Gson();

    /** 错误消息操作标签 */
    private static final String OP_DETECT = "detect";
    private static final String OP_ASYNC_SUBMIT = "async submit";
    private static final String OP_ASYNC_QUERY = "async query";
    private static final String OP_VIDEO_SUBMIT = "video submit";
    private static final String OP_VIDEO_QUERY = "video query";

    private final String accessKey;
    private final String appId;
    private final URI textUri;
    private final URI imageUri;
    private final URI imageAsyncUri;
    private final URI imageQueryUri;
    private final URI videoUri;
    private final URI videoQueryUri;
    private final HttpClient httpClient;
    private final Duration requestTimeout;

    /**
     * @param accessKey 数美接口认证密钥
     * @param appId 应用标识
     * @param properties 供应商配置（region/text-base-url/image-base-url/video-base-url 已解析）
     * @param connectTimeoutMs 连接超时毫秒
     * @param readTimeoutMs 读取超时毫秒
     */
    public HttpShumeiClientInvoker(String accessKey, String appId, ShumeiProviderProperties properties,
                                   int connectTimeoutMs, int readTimeoutMs) {
        this.accessKey = Asserts.notBlank(accessKey, "accessKey");
        this.appId = Asserts.notBlank(appId, "appId");
        this.textUri = parseUri(textBaseUrlOf(properties), ShumeiProviderConstants.EXTRA_TEXT_BASE_URL);
        String imageAuthority = imageAuthorityOf(properties);
        this.imageUri = parseUri(imageAuthority + ShumeiProviderConstants.IMAGE_PATH, null);
        this.imageAsyncUri = parseUri(imageAuthority + ShumeiProviderConstants.IMAGE_ASYNC_PATH, null);
        this.imageQueryUri = parseUri(imageAuthority + ShumeiProviderConstants.IMAGE_QUERY_PATH, null);
        String videoAuthority = videoAuthorityOf(properties);
        this.videoUri = parseUri(videoAuthority + ShumeiProviderConstants.VIDEO_PATH, null);
        this.videoQueryUri = parseUri(videoAuthority + ShumeiProviderConstants.VIDEO_QUERY_PATH, null);
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(connectTimeoutMs))
                .build();
        this.requestTimeout = Duration.ofMillis(readTimeoutMs);
    }

    @Override
    public ShumeiSyncResponse textDetect(String eventId, String type, Map<String, Object> data) {
        Asserts.notBlank(eventId, "eventId");
        Asserts.notBlank(type, "type");
        Asserts.notNull(data, "data");
        ShumeiSyncResponse parsed = post(textUri, envelope(eventId, type, data),
                ShumeiSyncResponse.class, OP_DETECT);
        return ShumeiErrorMapper.assertSuccess(parsed, OP_DETECT);
    }

    @Override
    public ShumeiSyncResponse imageDetect(String eventId, String type, Map<String, Object> data) {
        Asserts.notBlank(eventId, "eventId");
        Asserts.notBlank(type, "type");
        Asserts.notNull(data, "data");
        ShumeiSyncResponse parsed = post(imageUri, envelope(eventId, type, data),
                ShumeiSyncResponse.class, OP_DETECT);
        return ShumeiErrorMapper.assertSuccess(parsed, OP_DETECT);
    }

    @Override
    public ShumeiSyncResponse imageAsyncSubmit(String eventId, String type, Map<String, Object> data) {
        Asserts.notBlank(eventId, "eventId");
        Asserts.notBlank(type, "type");
        Asserts.notNull(data, "data");
        // 异步提交请求体与同步 image/v4 同构，响应回显请求并携带任务引用 requestId
        ShumeiSyncResponse parsed = post(imageAsyncUri, envelope(eventId, type, data),
                ShumeiSyncResponse.class, OP_ASYNC_SUBMIT);
        return ShumeiErrorMapper.assertSuccess(parsed, OP_ASYNC_SUBMIT);
    }

    @Override
    public ShumeiAsyncQueryResponse imageAsyncQuery(List<String> requestIds) {
        Asserts.notNull(requestIds, "requestIds");
        if (requestIds.isEmpty()) {
            throw new InvalidRequestException("requestIds must not be empty");
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("accessKey", accessKey);
        body.put("requestIds", requestIds);
        ShumeiAsyncQueryResponse parsed = post(imageQueryUri, body,
                ShumeiAsyncQueryResponse.class, OP_ASYNC_QUERY);
        return ShumeiErrorMapper.assertSuccess(parsed, OP_ASYNC_QUERY);
    }

    @Override
    public ShumeiVideoQueryResponse videoSubmit(String eventId, String imgType, String audioType,
                                                Map<String, Object> data) {
        Asserts.notBlank(eventId, "eventId");
        Asserts.notNull(data, "data");
        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("accessKey", accessKey);
        envelope.put("appId", appId);
        envelope.put("eventId", eventId);
        if (imgType != null && !imgType.isBlank()) {
            envelope.put("imgType", imgType);
        }
        if (audioType != null && !audioType.isBlank()) {
            envelope.put("audioType", audioType);
        }
        envelope.put("data", data);
        ShumeiVideoQueryResponse parsed = post(videoUri, envelope,
                ShumeiVideoQueryResponse.class, OP_VIDEO_SUBMIT);
        return ShumeiErrorMapper.assertVideoSuccess(parsed, OP_VIDEO_SUBMIT);
    }

    @Override
    public ShumeiVideoQueryResponse videoQuery(String btId) {
        Asserts.notBlank(btId, "btId");
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("accessKey", accessKey);
        body.put("btId", btId);
        // 1101 处理中是合法中间态，不按成功码断言，由操作层分流
        return post(videoQueryUri, body, ShumeiVideoQueryResponse.class, OP_VIDEO_QUERY);
    }

    private Map<String, Object> envelope(String eventId, String type, Map<String, Object> data) {
        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("accessKey", accessKey);
        envelope.put("appId", appId);
        envelope.put("eventId", eventId);
        envelope.put("type", type);
        envelope.put("data", data);
        return envelope;
    }

    private <T> T post(URI uri, Map<String, Object> envelope, Class<T> responseType, String op) {
        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(requestTimeout)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(GSON.toJson(envelope), StandardCharsets.UTF_8))
                .build();
        try {
            HttpResponse<InputStream> response =
                    httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
            T parsed = parse(response.body(), responseType);
            if (parsed == null) {
                throw new ProviderCallException(
                        "shumei " + op + " returned empty body, httpStatus=" + response.statusCode());
            }
            return parsed;
        } catch (IOException e) {
            throw ShumeiErrorMapper.fromException(e, op);
        } catch (JsonSyntaxException e) {
            // 报文非合法 JSON：归一为调用异常；message 不携带报文原文
            throw new ProviderCallException("shumei " + op + " returned invalid json body", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ProviderCallException("shumei " + op + " call interrupted", e);
        }
    }

    private static <T> T parse(InputStream body, Class<T> responseType) throws IOException {
        try (Reader reader = new InputStreamReader(body, StandardCharsets.UTF_8)) {
            return GSON.fromJson(reader, responseType);
        }
    }

    /**
     * URI 解析 fail-fast：非法 base-url 配置在装配期抛可读配置错误（带配置键名定位），
     * 而非裸 {@link IllegalArgumentException}
     */
    private static URI parseUri(String value, String configKey) {
        try {
            return URI.create(value);
        } catch (IllegalArgumentException e) {
            throw new ConfigInvalidException(
                    "shumei extra '" + (configKey == null ? "base-url" : configKey)
                            + "' is not a valid URI");
        }
    }

    private static String textBaseUrlOf(ShumeiProviderProperties properties) {
        String override = properties.extraValue(ShumeiProviderConstants.EXTRA_TEXT_BASE_URL);
        if (override != null) {
            return override;
        }
        return "https://" + ShumeiProviderConstants.TEXT_HOST_PREFIX + properties.getRegion()
                + ShumeiProviderConstants.HOST_SUFFIX + ShumeiProviderConstants.TEXT_PATH;
    }

    /**
     * 图片域主机（含 scheme）：异步提交/查询与同步共用 api-img-{region} 集群；
     * image-base-url 整体覆盖时取其 scheme+authority 再拼异步路径
     */
    private static String imageAuthorityOf(ShumeiProviderProperties properties) {
        String override = properties.extraValue(ShumeiProviderConstants.EXTRA_IMAGE_BASE_URL);
        if (override != null) {
            URI uri = parseUri(override, ShumeiProviderConstants.EXTRA_IMAGE_BASE_URL);
            return uri.getScheme() + "://" + uri.getRawAuthority();
        }
        return "https://" + ShumeiProviderConstants.IMAGE_HOST_PREFIX + properties.getRegion()
                + ShumeiProviderConstants.HOST_SUFFIX;
    }

    /**
     * 视频域主机（含 scheme）：提交/查询共用 api-video-{region} 集群；
     * video-base-url 整体覆盖时取其 scheme+authority 再拼查询路径
     */
    private static String videoAuthorityOf(ShumeiProviderProperties properties) {
        String override = properties.extraValue(ShumeiProviderConstants.EXTRA_VIDEO_BASE_URL);
        if (override != null) {
            URI uri = parseUri(override, ShumeiProviderConstants.EXTRA_VIDEO_BASE_URL);
            return uri.getScheme() + "://" + uri.getRawAuthority();
        }
        return "https://" + ShumeiProviderConstants.VIDEO_HOST_PREFIX + properties.getRegion()
                + ShumeiProviderConstants.HOST_SUFFIX;
    }
}
