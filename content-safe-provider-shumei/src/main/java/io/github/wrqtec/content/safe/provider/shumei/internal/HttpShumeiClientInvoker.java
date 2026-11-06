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
import java.util.Map;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
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

    private final String accessKey;
    private final String appId;
    private final URI textUri;
    private final URI imageUri;
    private final HttpClient httpClient;
    private final Duration requestTimeout;

    /**
     * @param accessKey 数美接口认证密钥
     * @param appId 应用标识
     * @param properties 供应商配置（region/text-base-url/image-base-url 已解析）
     * @param connectTimeoutMs 连接超时毫秒
     * @param readTimeoutMs 读取超时毫秒
     */
    public HttpShumeiClientInvoker(String accessKey, String appId, ShumeiProviderProperties properties,
                                   int connectTimeoutMs, int readTimeoutMs) {
        this.accessKey = Asserts.notBlank(accessKey, "accessKey");
        this.appId = Asserts.notBlank(appId, "appId");
        this.textUri = URI.create(textBaseUrlOf(properties));
        this.imageUri = URI.create(imageBaseUrlOf(properties));
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
        return post(textUri, envelope(eventId, type, data));
    }

    @Override
    public ShumeiSyncResponse imageDetect(String eventId, String type, Map<String, Object> data) {
        Asserts.notBlank(eventId, "eventId");
        Asserts.notBlank(type, "type");
        Asserts.notNull(data, "data");
        return post(imageUri, envelope(eventId, type, data));
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

    private ShumeiSyncResponse post(URI uri, Map<String, Object> envelope) {
        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(requestTimeout)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(GSON.toJson(envelope), StandardCharsets.UTF_8))
                .build();
        try {
            HttpResponse<InputStream> response =
                    httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
            ShumeiSyncResponse parsed = parse(response.body());
            if (parsed == null) {
                throw new ProviderCallException(
                        "shumei detect returned empty body, httpStatus=" + response.statusCode());
            }
            return ShumeiErrorMapper.assertSuccess(parsed);
        } catch (IOException e) {
            throw ShumeiErrorMapper.fromException(e);
        } catch (JsonSyntaxException e) {
            // 报文非合法 JSON：归一为调用异常；message 不携带报文原文
            throw new ProviderCallException("shumei detect returned invalid json body", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ProviderCallException("shumei detect call interrupted", e);
        }
    }

    private static ShumeiSyncResponse parse(InputStream body) throws IOException {
        try (Reader reader = new InputStreamReader(body, StandardCharsets.UTF_8)) {
            return GSON.fromJson(reader, ShumeiSyncResponse.class);
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

    private static String imageBaseUrlOf(ShumeiProviderProperties properties) {
        String override = properties.extraValue(ShumeiProviderConstants.EXTRA_IMAGE_BASE_URL);
        if (override != null) {
            return override;
        }
        return "https://" + ShumeiProviderConstants.IMAGE_HOST_PREFIX + properties.getRegion()
                + ShumeiProviderConstants.HOST_SUFFIX + ShumeiProviderConstants.IMAGE_PATH;
    }
}
