package io.github.wrqtec.content.safe.api;

/**
 * 回调处理结果，不可变
 *
 * @param accepted 是否受理（验签通过且报文合法；重复回调幂等受理也算 accepted）
 * @param taskId   关联的异步任务 ID，无法解析时为 null
 * @param result   归一化审核结果。受理（accepted=true）时必须非 null：
 *                 编排层据此对任务做终态迁移，受理但无结果的回调按死信处理、
 *                 不迁移任务状态（null result 会产出 query() 返回 null 的坏任务）
 * @param message  附加说明（拒收原因等），禁止携带报文原文与签名
 *
 * @author L.David
 */
public record CallbackResult(boolean accepted, String taskId, AuditResult result, String message) {

    /**
     * 构造受理结果
     *
     * @param taskId 关联的异步任务 ID，无法解析时为 {@code null}
     * @param result 归一化审核结果，必须非 null
     * @return 受理的回调结果
     */
    public static CallbackResult accepted(String taskId, AuditResult result) {
        return new CallbackResult(true, taskId, result, null);
    }

    /**
     * 构造拒收结果
     *
     * @param message 拒收原因，禁止携带报文原文与签名
     * @return 拒收的回调结果
     */
    public static CallbackResult rejected(String message) {
        return new CallbackResult(false, null, null, message);
    }
}
