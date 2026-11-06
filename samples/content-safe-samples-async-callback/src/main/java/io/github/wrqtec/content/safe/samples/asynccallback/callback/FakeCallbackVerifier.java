package io.github.wrqtec.content.safe.samples.asynccallback.callback;

import java.util.Map;

import io.github.wrqtec.content.safe.api.CallbackRequest;
import io.github.wrqtec.content.safe.spi.CallbackVerifier;
import org.springframework.stereotype.Component;

/**
 * 模拟回调验签：header {@code x-callback-token} 必须等于配置的 callback-secret。
 *
 * <p>该 Bean 同时是 {@code CallbackDispatcher} 的装配开关
 * （{@code @ConditionalOnBean(CallbackVerifier.class)}）。
 *
 * @author L.David
 */
@Component
public class FakeCallbackVerifier implements CallbackVerifier {

    /** 验签 header 名（CallbackRequest 契约：headers key 统一小写） */
    public static final String TOKEN_HEADER = "x-callback-token";

    @Override
    public String provider() {
        return "fake-async";
    }

    @Override
    public boolean verify(CallbackRequest request, String secret, Map<String, String> ext) {
        return secret != null && secret.equals(request.headers().get(TOKEN_HEADER));
    }
}
