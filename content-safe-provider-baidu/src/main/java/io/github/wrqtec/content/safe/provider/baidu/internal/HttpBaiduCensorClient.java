package io.github.wrqtec.content.safe.provider.baidu.internal;

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
import io.github.wrqtec.content.safe.provider.baidu.BaiduProviderConstants;

/**
 * 百度内容审核 HTTP 客户端（JDK HttpClient + Gson）
 *
 * <p>form-urlencoded POST；同步审核遇 error_code 110/111 使 token 失效并重试一次。
 * 长视频提交/拉取同样携带 access_token，失败不就地重试（轮询天然重试）
 *
 * @author L.David
 */
public class HttpBaiduCensorClient implements BaiduCensorClient {

    private static final Gson GSON = new Gson();

    /** token 失效重试次数 */
    private static final int TOKEN_RETRY_TIMES = 1;

    private final BaiduAccessTokenManager tokenManager;
    private final URI textUri;
    private final URI imageUri;
    private final URI videoSubmitUri;
    private final URI videoPullUri;
    private final HttpClient httpClient;
    private final Duration requestTimeout;

    /**
     * @param tokenManager token 管理器
     * @param baseUrl API 基础地址
     * @param connectTimeoutMs 连接超时毫秒
     * @param readTimeoutMs 读取超时毫秒
     */
    public HttpBaiduCensorClient(BaiduAccessTokenManager tokenManager, String baseUrl,
                                 int connectTimeoutMs, int readTimeoutMs) {
        this.tokenManager = Asserts.notNull(tokenManager, "tokenManager");
        Asserts.notBlank(baseUrl, "baseUrl");
        this.textUri = URI.create(baseUrl + BaiduProviderConstants.TEXT_CENSOR_PATH);
        this.imageUri = URI.create(baseUrl + BaiduProviderConstants.IMAGE_CENSOR_PATH);
        this.videoSubmitUri = URI.create(baseUrl + BaiduProviderConstants.VIDEO_SUBMIT_PATH);
        this.videoPullUri = URI.create(baseUrl + BaiduProviderConstants.VIDEO_PULL_PATH);
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(connectTimeoutMs))
                .build();
        this.requestTimeout = Duration.ofMillis(readTimeoutMs);
    }

    @Override
    public BaiduCensorResponse textCensor(String text, String strategyId) {
        Asserts.notNull(text, "text");
        return callWithTokenRetry(textUri, formOf("text", text, strategyId));
    }

    @Override
    public BaiduCensorResponse imageCensorByBase64(String base64, String strategyId) {
        Asserts.notBlank(base64, "base64");
        return callWithTokenRetry(imageUri, formOf("image", base64, strategyId));
    }

    @Override
    public BaiduCensorResponse imageCensorByUrl(String imgUrl, String strategyId) {
        Asserts.notBlank(imgUrl, "imgUrl");
        return callWithTokenRetry(imageUri, formOf("imgUrl", imgUrl, strategyId));
    }

    @Override
    public BaiduVideoSubmitResponse videoSubmit(String url, String extId, String strategyId,
                                                String noticeUrl, String subEvents) {
        Asserts.notBlank(url, "url");
        Asserts.notBlank(extId, "extId");
        FormBody form = new FormBody()
                .put("url", url)
                .put("extId", extId)
                .putIfNotBlank("strategyId", strategyId)
                .putIfNotBlank("noticeUrl", noticeUrl)
                .putIfNotBlank("subEvents", subEvents);
        BaiduVideoSubmitResponse response = send(
                videoSubmitUri, form, tokenManager.getToken(), BaiduVideoSubmitResponse.class, "video");
        return BaiduErrorMapper.assertVideoSuccess(response);
    }

    @Override
    public BaiduVideoPullResponse videoPull(String taskId) {
        Asserts.notBlank(taskId, "taskId");
        FormBody form = new FormBody()
                .put("taskId", taskId)
                .putIfNotBlank("subEvents", BaiduProviderConstants.PULL_SUB_EVENTS_ALL);
        BaiduVideoPullResponse response = send(
                videoPullUri, form, tokenManager.getToken(), BaiduVideoPullResponse.class, "video");
        return BaiduErrorMapper.assertVideoSuccess(response);
    }

    private BaiduCensorResponse callWithTokenRetry(URI uri, FormBody form) {
        for (int attempt = 0; ; attempt++) {
            BaiduCensorResponse response = send(
                    uri, form, tokenManager.getToken(), BaiduCensorResponse.class, "censor");
            if (!BaiduErrorMapper.isTokenError(response.getErrorCode()) || attempt >= TOKEN_RETRY_TIMES) {
                return BaiduErrorMapper.assertSuccess(response);
            }
            tokenManager.invalidate();
        }
    }

    private <T> T send(URI uri, FormBody form, String accessToken, Class<T> type, String api) {
        URI uriWithToken = URI.create(uri + "?access_token=" + BaiduAccessTokenManager.urlEncode(accessToken));
        HttpRequest request = HttpRequest.newBuilder(uriWithToken)
                .timeout(requestTimeout)
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(form.encode(), StandardCharsets.UTF_8))
                .build();
        try {
            HttpResponse<InputStream> response =
                    httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
            T parsed = parse(response.body(), type);
            if (parsed == null) {
                throw new ProviderCallException(
                        "baidu " + api + " returned empty body, httpStatus=" + response.statusCode());
            }
            return parsed;
        } catch (IOException e) {
            throw BaiduErrorMapper.fromException(e);
        } catch (JsonSyntaxException e) {
            // 报文非合法 JSON：归一为调用异常；message 不携带报文原文
            throw new ProviderCallException("baidu " + api + " returned invalid json body", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ProviderCallException("baidu " + api + " call interrupted", e);
        }
    }

    private static <T> T parse(InputStream body, Class<T> type) throws IOException {
        try (Reader reader = new InputStreamReader(body, StandardCharsets.UTF_8)) {
            return GSON.fromJson(reader, type);
        }
    }

    private static FormBody formOf(String contentKey, String contentValue, String strategyId) {
        return new FormBody()
                .put(contentKey, contentValue)
                .putIfNotBlank("strategyId", strategyId);
    }

    /**
     * 表单体：按序收集键值对，空白值跳过
     */
    private static final class FormBody {

        private final Map<String, String> entries = new LinkedHashMap<>();

        FormBody put(String key, String value) {
            entries.put(key, value);
            return this;
        }

        FormBody putIfNotBlank(String key, String value) {
            if (value != null && !value.isBlank()) {
                entries.put(key, value);
            }
            return this;
        }

        String encode() {
            StringBuilder body = new StringBuilder(128);
            for (Map.Entry<String, String> entry : entries.entrySet()) {
                if (body.length() > 0) {
                    body.append('&');
                }
                body.append(entry.getKey()).append('=')
                        .append(BaiduAccessTokenManager.urlEncode(entry.getValue()));
            }
            return body.toString();
        }
    }
}
