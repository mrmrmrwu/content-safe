package io.github.wrqtec.content.safe.provider.tencent.internal;

import java.util.Map;

import com.tencentcloudapi.common.AbstractModel;
import com.tencentcloudapi.ims.v20201229.models.ImageModerationResponse;
import io.github.wrqtec.content.safe.api.CallbackRequest;
import io.github.wrqtec.content.safe.provider.tencent.TencentProviderConstants;
import io.github.wrqtec.content.safe.spi.CallbackVerifier;
import lombok.extern.slf4j.Slf4j;

/**
 * 腾讯云回调验签器。官方图片异步审核回调无签名机制
 * （cloud.tencent.com/document/product/1125/87021，查阅 2026-11-07），
 * 仅做结构校验：报文可按同步 ImageModeration 输出格式反序列化即放行。
 * 官方协议无签名/时间戳/nonce，本验签<b>不防伪也不防重放</b>：任何能投递到回调地址的
 * 报文都会被受理（终态迁移幂等，重复投递无二次事件）。防重放是 CallbackVerifier 的
 * 契约责任，本实现受协议所限无法履行，部署方必须在网络层补偿：回调地址仅内网可达、
 * 来源 IP 白名单、网关限流（另见 content.safe.callback.rate-limit）。secret 参数
 * 不参与校验，仅为满足 SPI 契约保留
 *
 * @author L.David
 */
@Slf4j
public class TencentCallbackVerifier implements CallbackVerifier {

    @Override
    public String provider() {
        return TencentProviderConstants.PROVIDER;
    }

    @Override
    public boolean verify(CallbackRequest request, String secret, Map<String, String> ext) {
        String rawBody = request.rawBody();
        if (rawBody == null || rawBody.isBlank()) {
            log.warn("tencent callback verify failed: empty body");
            return false;
        }
        try {
            ImageModerationResponse response = AbstractModel.fromJsonString(rawBody, ImageModerationResponse.class);
            return response != null;
        } catch (Exception e) {
            log.warn("tencent callback verify failed: payload is not a valid ImageModeration payload");
            return false;
        }
    }
}
