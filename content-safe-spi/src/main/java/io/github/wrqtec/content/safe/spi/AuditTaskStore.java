package io.github.wrqtec.content.safe.spi;

import java.util.Optional;

import io.github.wrqtec.content.safe.api.AuditResult;
import io.github.wrqtec.content.safe.model.AuditTask;
import io.github.wrqtec.content.safe.model.enums.TaskStatus;

/**
 * 异步任务持久化。core 通过该 SPI 读写任务；默认提供内存实现，生产应替换为 DB/Redis
 *
 * @author L.David
 */
public interface AuditTaskStore {

    /**
     * 保存新任务。taskId 必须全局唯一
     *
     * @param task 待保存的任务，不得为 null
     */
    void save(AuditTask task);

    /**
     * 按 taskId 查询
     *
     * @param taskId 任务 ID
     * @return 命中的任务；不存在时返回 {@link Optional#empty()}
     */
    Optional<AuditTask> find(String taskId);

    /**
     * 按供应商侧引用查询（回调反查）
     *
     * @param providerTaskId 供应商侧任务 ID
     * @param dataId         业务侧数据唯一标识，可为 null（表示不约束）
     * @return 命中的任务；不存在时返回 {@link Optional#empty()}
     */
    Optional<AuditTask> findByRef(String providerTaskId, String dataId);

    /**
     * 终态迁移：成功返回 true；已终态返回 false（幂等）
     *
     * @param taskId 任务 ID
     * @param status 目标终态
     * @param result 审核结果，可为 null
     * @return true 表示迁移成功；任务不存在或已终态时返回 false
     */
    boolean markTerminal(String taskId, TaskStatus status, AuditResult result);

    /**
     * 删除任务
     *
     * @param taskId 任务 ID
     */
    void delete(String taskId);
}
