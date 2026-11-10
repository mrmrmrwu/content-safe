package io.github.wrqtec.content.safe.spi;

import java.util.Map;

import io.github.wrqtec.content.safe.api.CallbackRequest;
import io.github.wrqtec.content.safe.api.exception.CallbackVerifyException;

/**
 * 回调验签。provider 必须实现该接口；core 收到回调时先调用 {@link #verify}，
 * 失败即抛 {@link CallbackVerifyException}，并发布死信事件
 *
 * <p><b>防重放契约（必选责任）</b>：验签必须包含时效性校验——回调载荷携带时间戳时
 * 必须校验时间窗（拒绝过旧或未来的时间戳），携带 nonce/流水号时可配合
 * {@link AuditTaskStore} 等一次性消费机制去重；协议本身不含任何时间要素时，
 * 实现类 javadoc 必须如实声明"本验签不防重放"，并提示部署方在网络层补偿
 * （回调地址仅内网可达、来源 IP 白名单、网关限流）。只验签名不验时效的实现
 * 等于允许任何一次截获的合法回调被无限重放
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
     * @param request 回调原文（headers + rawBody），禁止在日志输出 rawBody 全文
     * @param secret  从配置解析的密钥，禁止出现在日志
     * @param ext     扩展参数。<b>内置调度器当前恒传空 Map</b>（各内置 verifier 的专属配置
     *                在装配期已通过构造器注入）；该参数保留给自定义调度器传递部署上下文。
     *                实现不得依赖其非空
     * @return true 验签通过；false 不通过
     */
    boolean verify(CallbackRequest request, String secret, Map<String, String> ext);
}
