package io.github.wrqtec.content.safe.provider.baidu.internal;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;

import com.sun.net.httpserver.HttpServer;
import io.github.wrqtec.content.safe.api.exception.ProviderCallException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link BaiduAccessTokenManager} 传输与缓存行为测试（本地 HttpServer）：
 * 非 200 拒绝、invalidate(expectedToken) 竞态防护
 *
 * @author L.David
 */
class BaiduAccessTokenManagerTest {

    private HttpServer server;
    private String baseUrl;
    private volatile int status = 200;
    private final AtomicInteger tokenRequests = new AtomicInteger();

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            tokenRequests.incrementAndGet();
            byte[] body = "{\"access_token\":\"token-1\",\"expires_in\":2592000}"
                    .getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status, body.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(body);
            }
        });
        server.start();
        baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    private BaiduAccessTokenManager manager() {
        return new BaiduAccessTokenManager("ak", "sk", baseUrl, 3000, 5000);
    }

    @Test
    void non200RejectedBeforeParsing() {
        status = 500;
        assertThatThrownBy(() -> manager().getToken())
                .isInstanceOf(ProviderCallException.class)
                .hasMessageContaining("httpStatus=500");
    }

    @Test
    void invalidateWithMismatchedTokenKeepsNewerCache() {
        BaiduAccessTokenManager manager = manager();
        String token = manager.getToken();
        assertThat(token).isEqualTo("token-1");
        assertThat(tokenRequests).hasValue(1);

        // 模拟竞态：旧请求携带过期 token 失败，但缓存已是新 token——不得误清
        manager.invalidate("stale-token");
        assertThat(manager.getToken()).isEqualTo("token-1");
        assertThat(tokenRequests).hasValue(1);

        // 比对一致才真正失效并重新拉取
        manager.invalidate("token-1");
        manager.getToken();
        assertThat(tokenRequests).hasValue(2);
    }
}
