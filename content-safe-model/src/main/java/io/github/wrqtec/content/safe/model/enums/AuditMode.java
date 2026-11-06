package io.github.wrqtec.content.safe.model.enums;

/**
 * 供应商操作模式
 *
 * @author L.David
 */
public enum AuditMode {

    /** 同步单条 */
    SYNC,
    /** 同步批量 */
    SYNC_BATCH,
    /** 异步提交 + 主动查询 */
    ASYNC_QUERY,
    /** 异步提交 + 回调推送 */
    ASYNC_CALLBACK
}
