package io.github.wrqtec.content.safe.provider.baidu.internal;

import java.util.Map;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import io.github.wrqtec.content.safe.api.CallbackRequest;
import io.github.wrqtec.content.safe.provider.baidu.BaiduProviderConstants;
import io.github.wrqtec.content.safe.spi.CallbackVerifier;
import lombok.extern.slf4j.Slf4j;

/**
 * 百度回调验签器。官方长视频审核结果推送协议无签名机制
 * （内容审核-接收视频审核结果 8ksy7dc4b，查阅 2026-11-11），仅做结构校验：
 * 报文可按推送 JSON 结构（taskId/extId/result 等字段）反序列化即放行。
 * 官方协议无签名/时间戳/nonce，本验签<b>不防伪也不防重放</b>：任何能投递到回调地址的
 * 报文都会被受理（终态迁移幂等，重复投递无二次事件）。防重放是 CallbackVerifier 的
 * 契约责任，本实现受协议所限无法履行，部署方必须在网络层补偿：回调地址仅内网可达、
 * 来源 IP 白名单、网关限流（另见 content.safe.callback.rate-limit）。secret 参数
 * 不参与校验，仅为满足 SPI 契约保留
 *
 * @author L.David
 */
@Slf4j
public class BaiduCallbackVerifier implements CallbackVerifier {

    private static final Gson GSON = new Gson();

    @Override
    public String provider() {
        return BaiduProviderConstants.PROVIDER;
    }

    @Override
    public boolean verify(CallbackRequest request, String secret, Map<String, String> ext) {
        String rawBody = request.rawBody();
        if (rawBody == null || rawBody.isBlank()) {
            log.warn("baidu callback verify failed: empty body");
            return false;
        }
        try {
            BaiduVideoCallbackPayload payload = GSON.fromJson(rawBody, BaiduVideoCallbackPayload.class);
            return payload != null;
        } catch (JsonSyntaxException e) {
            log.warn("baidu callback verify failed: payload is not a valid video callback payload");
            return false;
        }
    }
}
