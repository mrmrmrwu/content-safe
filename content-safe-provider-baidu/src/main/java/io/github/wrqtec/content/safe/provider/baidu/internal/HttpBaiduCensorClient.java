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

import com.google.gson.Gson;
import io.github.wrqtec.content.safe.api.exception.ProviderCallException;
import io.github.wrqtec.content.safe.api.util.Asserts;
import io.github.wrqtec.content.safe.provider.baidu.BaiduProviderConstants;

/**
 * 百度内容审核 HTTP 客户端（JDK HttpClient + Gson）
 *
 * <p>form-urlencoded POST；遇 error_code 110/111 使 token 失效并重试一次。
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
        this.tokenManager = Asserts.notNull(tokenManager, "tokenManager must not be null");
        Asserts.notBlank(baseUrl, "baseUrl must not be blank");
        this.textUri = URI.create(baseUrl + BaiduProviderConstants.TEXT_CENSOR_PATH);
        this.imageUri = URI.create(baseUrl + BaiduProviderConstants.IMAGE_CENSOR_PATH);
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(connectTimeoutMs))
                .build();
        this.requestTimeout = Duration.ofMillis(readTimeoutMs);
    }

    @Override
    public BaiduCensorResponse textCensor(String text, String strategyId) {
        Asserts.notNull(text, "text must not be null");
        return callWithTokenRetry(textUri, formOf("text", text, strategyId));
    }

    @Override
    public BaiduCensorResponse imageCensorByBase64(String base64, String strategyId) {
        Asserts.notBlank(base64, "base64 must not be blank");
        return callWithTokenRetry(imageUri, formOf("image", base64, strategyId));
    }

    @Override
    public BaiduCensorResponse imageCensorByUrl(String imgUrl, String strategyId) {
        Asserts.notBlank(imgUrl, "imgUrl must not be blank");
        return callWithTokenRetry(imageUri, formOf("imgUrl", imgUrl, strategyId));
    }

    private BaiduCensorResponse callWithTokenRetry(URI uri, FormBody form) {
        for (int attempt = 0; ; attempt++) {
            BaiduCensorResponse response = post(uri, form, tokenManager.getToken());
            if (!BaiduErrorMapper.isTokenError(response.getErrorCode()) || attempt >= TOKEN_RETRY_TIMES) {
                return BaiduErrorMapper.assertSuccess(response);
            }
            tokenManager.invalidate();
        }
    }

    private BaiduCensorResponse post(URI uri, FormBody form, String accessToken) {
        URI uriWithToken = URI.create(uri + "?access_token=" + BaiduAccessTokenManager.urlEncode(accessToken));
        HttpRequest request = HttpRequest.newBuilder(uriWithToken)
                .timeout(requestTimeout)
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(form.encode(), StandardCharsets.UTF_8))
                .build();
        try {
            HttpResponse<InputStream> response =
                    httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
            BaiduCensorResponse parsed = parse(response.body());
            if (parsed == null) {
                throw new ProviderCallException(
                        "baidu censor returned empty body, httpStatus=" + response.statusCode());
            }
            return parsed;
        } catch (IOException e) {
            throw BaiduErrorMapper.fromException(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ProviderCallException("baidu censor call interrupted", e);
        }
    }

    private static BaiduCensorResponse parse(InputStream body) throws IOException {
        try (Reader reader = new InputStreamReader(body, StandardCharsets.UTF_8)) {
            return GSON.fromJson(reader, BaiduCensorResponse.class);
        }
    }

    private static FormBody formOf(String contentKey, String contentValue, String strategyId) {
        FormBody form = new FormBody(contentKey, contentValue);
        if (strategyId != null && !strategyId.isBlank()) {
            form.strategyId = strategyId;
        }
        return form;
    }

    /**
     * 表单体：内容参数 + 可选 strategyId
     */
    private static class FormBody {

        private final String contentKey;
        private final String contentValue;
        private String strategyId;

        FormBody(String contentKey, String contentValue) {
            this.contentKey = contentKey;
            this.contentValue = contentValue;
        }

        String encode() {
            StringBuilder body = new StringBuilder(128);
            body.append(contentKey).append('=').append(BaiduAccessTokenManager.urlEncode(contentValue));
            if (strategyId != null) {
                body.append("&strategyId=").append(strategyId);
            }
            return body.toString();
        }
    }
}
