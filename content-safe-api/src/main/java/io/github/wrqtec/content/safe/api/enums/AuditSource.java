package io.github.wrqtec.content.safe.api.enums;

/**
 * 审核结果来源。
 *
 * @author L.David
 */
public enum AuditSource {

    /** 同步调用直接返回 */
    SYNC,
    /** 异步任务主动查询 */
    ASYNC_QUERY,
    /** 异步回调推送 */
    ASYNC_CALLBACK,
    /** 多供应商仲裁聚合 */
    ARBITRATION
}
