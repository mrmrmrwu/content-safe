package io.github.wrqtec.content.safe.model;

import java.time.Instant;

import io.github.wrqtec.content.safe.api.AuditResult;
import io.github.wrqtec.content.safe.api.util.Asserts;
import io.github.wrqtec.content.safe.model.enums.TaskStatus;

/**
 * 异步审核任务。不可变，状态变更即新建（{@link #withTerminal}）。
 * {@code version} 单调递增，用于重复/乱序回调的幂等裁决。不可变
 *
 * @author L.David
 */
public record AuditTask(
        String taskId,
        AsyncTaskRef ref,
        String scene,
        String provider,
        String operation,
        TaskStatus status,
        AuditResult result,
        long version,
        Instant createdAt,
        Instant updatedAt) {

    public AuditTask {
        Asserts.notBlank(taskId, "taskId");
        Asserts.notNull(ref, "ref");
        Asserts.notNull(status, "status");
        Instant now = Instant.now();
        createdAt = createdAt == null ? now : createdAt;
        updatedAt = updatedAt == null ? now : updatedAt;
    }

    /**
     * 创建 PENDING 状态的新任务（version 从 0 开始），不记录 operation
     * （同一供应商有多个异步操作时应改用 5 参重载）
     *
     * @param taskId   全局唯一任务 ID
     * @param ref      供应商侧任务引用
     * @param scene    业务场景 code
     * @param provider 供应商 key
     * @return PENDING 状态的任务实例
     */
    public static AuditTask pending(String taskId, AsyncTaskRef ref, String scene, String provider) {
        return pending(taskId, ref, scene, provider, null);
    }

    /**
     * 创建 PENDING 状态的新任务（version 从 0 开始）
     *
     * @param taskId    全局唯一任务 ID
     * @param ref       供应商侧任务引用
     * @param scene     业务场景 code
     * @param provider  供应商 key
     * @param operation 提交操作的 planKey（provider:operation）。同一供应商挂多个异步操作时，
     *                  查询按它精确路由，避免按 provider 匹配拿到错误操作；可为 null
     * @return PENDING 状态的任务实例
     */
    public static AuditTask pending(String taskId, AsyncTaskRef ref, String scene, String provider,
                                    String operation) {
        return new AuditTask(taskId, ref, scene, provider, operation,
                TaskStatus.PENDING, null, 0L, null, null);
    }

    /**
     * 终态迁移。已终态的任务再次迁移返回 null（调用方据此幂等放行）
     *
     * @param status 目标终态（SUCCEEDED/FAILED）
     * @param result 审核结果，可为 null
     * @return 迁移后的新任务实例；已终态时返回 null
     */
    public AuditTask withTerminal(TaskStatus status, AuditResult result) {
        if (this.status.isTerminal()) {
            return null;
        }
        return new AuditTask(taskId, ref, scene, provider, operation, status, result,
                version + 1, createdAt, Instant.now());
    }
}
