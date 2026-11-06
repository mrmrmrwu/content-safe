package io.github.wrqtec.content.safe.spi;

import java.util.Map;

import io.github.wrqtec.content.safe.api.CallbackRequest;
import io.github.wrqtec.content.safe.api.exception.CallbackVerifyException;

/**
 * 回调验签。provider 必须实现该接口；core 收到回调时先调用 {@link #verify}，
 * 失败即抛 {@link CallbackVerifyException}，并发布死信事件
 *
 * @author L.David
 */
public interface CallbackVerifier {

    /**
     * 供应商 key（与 provider() 一致）
     *
     * @return 供应商 key
     */
    String provider();

    /**
     * 验签
     *
     * @param request  回调原文（headers + rawBody）
     * @param secret   从配置解析的密钥，禁止出现在日志
     * @param ext      扩展参数（如时间戳、随机串）
     * @return true 验签通过；false 不通过
     */
    boolean verify(CallbackRequest request, String secret, Map<String, String> ext);
}
