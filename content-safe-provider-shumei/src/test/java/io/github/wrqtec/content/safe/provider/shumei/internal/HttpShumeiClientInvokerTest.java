package io.github.wrqtec.content.safe.provider.shumei.internal;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import com.sun.net.httpserver.HttpServer;
import io.github.wrqtec.content.safe.api.exception.ProviderCallException;
import io.github.wrqtec.content.safe.provider.shumei.ShumeiProviderProperties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link HttpShumeiClientInvoker} 传输行为测试（本地 HttpServer，不调真实云接口）：
 * 非 200 拒绝、响应体超限拒绝、正常 200 解析
 *
 * @author L.David
 */
class HttpShumeiClientInvokerTest {

    private HttpServer server;
    private String baseUrl;
    private volatile int status = 200;
    private volatile byte[] body = "{\"code\":1100}".getBytes(StandardCharsets.UTF_8);

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
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

    private HttpShumeiClientInvoker invoker() {
        ShumeiProviderProperties properties = new ShumeiProviderProperties();
        properties.getExtra().put("text-base-url", baseUrl + "/text/v4");
        properties.getExtra().put("image-base-url", baseUrl + "/image/v4");
        return new HttpShumeiClientInvoker("ak", "default", properties, 3000, 5000);
    }

    @Test
    void non200RejectedBeforeParsing() {
        status = 502;
        body = "<html>bad gateway</html>".getBytes(StandardCharsets.UTF_8);
        assertThatThrownBy(() -> invoker().textDetect("text", "TEXTRISK", Map.of("text", "t")))
                .isInstanceOf(ProviderCallException.class)
                .hasMessageContaining("httpStatus=502");
    }

    @Test
    void oversizedResponseRejected() {
        // 超过 4MiB 上限的响应必须在解析前被截断拒绝
        body = new byte[BoundedInputStream.DEFAULT_MAX_RESPONSE_BYTES + 1024];
        java.util.Arrays.fill(body, (byte) ' ');
        assertThatThrownBy(() -> invoker().textDetect("text", "TEXTRISK", Map.of("text", "t")))
                .isInstanceOf(ProviderCallException.class);
    }

    @Test
    void normal200Parsed() {
        ShumeiSyncResponse response = invoker().textDetect("text", "TEXTRISK", Map.of("text", "t"));
        assertThat(response.getCode()).isEqualTo(1100);
    }

    @Test
    void customHttpClientAccepted() {
        java.net.http.HttpClient custom = java.net.http.HttpClient.newBuilder().build();
        ShumeiProviderProperties properties = new ShumeiProviderProperties();
        properties.getExtra().put("text-base-url", baseUrl + "/text/v4");
        HttpShumeiClientInvoker invoker = new HttpShumeiClientInvoker("ak", "default", properties,
                3000, 5000, custom);
        assertThat(invoker.textDetect("text", "TEXTRISK", Map.of("text", "t")).getCode()).isEqualTo(1100);
    }
}
