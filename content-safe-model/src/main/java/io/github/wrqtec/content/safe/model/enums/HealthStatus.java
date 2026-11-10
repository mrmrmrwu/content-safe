package io.github.wrqtec.content.safe.model.enums;

/**
 * 供应商健康状态
 *
 * @author L.David
 * @deprecated 仅被弃用的 {@code ProviderHealth}/{@code ProviderHealthProbe} 使用
 *     （1.0.0 起无生产路径实例），可用性由熔断器与限流护栏在调用路径上动态判定。
 *     因 1.0.0 已发布无法物理删除（二进制兼容门禁），计划 2.0.0 一并移除
 */
@Deprecated(since = "1.1.0", forRemoval = true)
public enum HealthStatus {

    /** 健康，可正常承接流量 */
    UP,
    /** 降级，可承接流量但需关注（延迟升高或失败率上升） */
    DEGRADED,
    /** 不可用，熔断隔离 */
    DOWN,
    /** 未配置凭据或未启用 */
    NOT_CONFIGURED
}
