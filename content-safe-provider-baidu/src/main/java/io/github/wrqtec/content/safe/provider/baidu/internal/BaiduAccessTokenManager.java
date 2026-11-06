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
import java.time.Instant;

import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;
import io.github.wrqtec.content.safe.api.exception.ProviderCallException;
import io.github.wrqtec.content.safe.api.util.Asserts;
import lombok.extern.slf4j.Slf4j;

/**
 * 百度 access_token 管理器
 *
 * <p>client_credentials 模式，token 有效期 30 天（expires_in），本地缓存复用，
 * 提前 {@value #EXPIRY_MARGIN_SECONDS} 秒视为过期；遇 110/111 错误时由调用方
 * 调用 {@link #invalidate()} 后重取。
 *
 * @author L.David
 */
@Slf4j
public class BaiduAccessTokenManager {

    /** 提前视为过期的余量（秒） */
    private static final long EXPIRY_MARGIN_SECONDS = 300L;

    private static final Gson GSON = new Gson();

    private final String apiKey;
    private final String secretKey;
    private final URI tokenUri;
    private final HttpClient httpClient;
    private final Duration requestTimeout;

    /** 缓存的 token 与其过期时刻（epoch 秒） */
    private String cachedToken;
    private long expiresAtEpochSeconds;

    /**
     * @param apiKey 百度 API Key
     * @param secretKey 百度 Secret Key
     * @param baseUrl API 基础地址（如 https://aip.baidubce.com）
     * @param connectTimeoutMs 连接超时毫秒
     * @param readTimeoutMs 读取超时毫秒
     */
    public BaiduAccessTokenManager(String apiKey, String secretKey, String baseUrl,
                                   int connectTimeoutMs, int readTimeoutMs) {
        this.apiKey = Asserts.notBlank(apiKey, "apiKey must not be blank");
        this.secretKey = Asserts.notBlank(secretKey, "secretKey must not be blank");
        Asserts.notBlank(baseUrl, "baseUrl must not be blank");
        this.tokenUri = URI.create(baseUrl + io.github.wrqtec.content.safe.provider.baidu
                .BaiduProviderConstants.TOKEN_PATH);
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(connectTimeoutMs))
                .build();
        this.requestTimeout = Duration.ofMillis(readTimeoutMs);
    }

    /**
     * 获取有效 token（缓存命中直接返回）
     */
    public synchronized String getToken() {
        long now = Instant.now().getEpochSecond();
        if (cachedToken != null && now < expiresAtEpochSeconds - EXPIRY_MARGIN_SECONDS) {
            return cachedToken;
        }
        return fetchToken();
    }

    /**
     * 使缓存 token 失效（遇 error_code 110/111 时调用，下次 getToken 重新拉取）
     */
    public synchronized void invalidate() {
        cachedToken = null;
        expiresAtEpochSeconds = 0L;
    }

    private String fetchToken() {
        String body = "grant_type=client_credentials"
                + "&client_id=" + urlEncode(apiKey)
                + "&client_secret=" + urlEncode(secretKey);
        HttpRequest request = HttpRequest.newBuilder(tokenUri)
                .timeout(requestTimeout)
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build();
        TokenResponse tokenResponse;
        try {
            HttpResponse<InputStream> response =
                    httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
            tokenResponse = parse(response.body());
        } catch (IOException e) {
            throw new ProviderCallException("baidu access_token request failed: " + e.getClass().getSimpleName(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ProviderCallException("baidu access_token request interrupted", e);
        }
        if (tokenResponse == null || tokenResponse.accessToken == null || tokenResponse.accessToken.isBlank()) {
            String reason = tokenResponse == null ? "empty response"
                    : BaiduErrorMapper.sanitize("error=" + nullToEmpty(tokenResponse.error)
                    + " " + nullToEmpty(tokenResponse.errorDescription));
            throw new ProviderCallException("baidu access_token obtain failed: " + reason);
        }
        long ttl = tokenResponse.expiresIn != null ? tokenResponse.expiresIn : 0L;
        this.cachedToken = tokenResponse.accessToken;
        this.expiresAtEpochSeconds = Instant.now().getEpochSecond() + ttl;
        log.debug("baidu access_token refreshed, ttlSeconds={}", ttl);
        return cachedToken;
    }

    private static TokenResponse parse(InputStream body) throws IOException {
        try (Reader reader = new InputStreamReader(body, StandardCharsets.UTF_8)) {
            return GSON.fromJson(reader, TokenResponse.class);
        }
    }

    static String urlEncode(String value) {
        return java.net.URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    /**
     * token 接口响应（成功时 error 字段为空）
     */
    private static class TokenResponse {

        @SerializedName("access_token")
        private String accessToken;

        @SerializedName("expires_in")
        private Long expiresIn;

        private String error;

        @SerializedName("error_description")
        private String errorDescription;
    }
}
