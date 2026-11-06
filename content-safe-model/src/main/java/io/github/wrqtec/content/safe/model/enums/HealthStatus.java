package io.github.wrqtec.content.safe.model.enums;

/**
 * 供应商健康状态
 *
 * @author L.David
 */
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
