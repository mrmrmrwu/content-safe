package io.github.wrqtec.content.safe.provider.shumei.internal;

import java.util.Map;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import io.github.wrqtec.content.safe.api.CallbackRequest;
import io.github.wrqtec.content.safe.provider.shumei.ShumeiProviderConstants;
import io.github.wrqtec.content.safe.spi.CallbackVerifier;
import lombok.extern.slf4j.Slf4j;

/**
 * 数美回调校验（结构校验型）
 *
 * <p>官方异步回调无签名/Checksum 机制（回调报文与同步 image/v4 响应同构，
 * github.com/ishumei/api-doc，2026-11-07 调研；HTTP 200 确认，否则按
 * [1,2,3,4,5,6,7,8] 秒重试 8 次）。此处仅校验报文为合法 JSON 且结构可解析；
 * 防伪需依赖回调接收侧 IP 白名单等业务手段。
 *
 * @author L.David
 */
@Slf4j
public class ShumeiCallbackVerifier implements CallbackVerifier {

    private static final Gson GSON = new Gson();

    @Override
    public String provider() {
        return ShumeiProviderConstants.PROVIDER;
    }

    @Override
    public boolean verify(CallbackRequest request, String callbackSecret, Map<String, String> ext) {
        if (request == null || request.rawBody() == null || request.rawBody().isBlank()) {
            log.warn("shumei callback verify failed: empty body");
            return false;
        }
        try {
            ShumeiSyncResponse response = GSON.fromJson(request.rawBody(), ShumeiSyncResponse.class);
            if (response == null) {
                log.warn("shumei callback verify failed: not a valid payload object");
                return false;
            }
            return true;
        } catch (JsonSyntaxException e) {
            log.warn("shumei callback verify failed: invalid json payload");
            return false;
        }
    }
}
