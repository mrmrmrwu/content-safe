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
     * 按供应商侧引用查询（回调反查）。providerTaskId 空白时退化为按 dataId 反查
     * （部分供应商回调不回显任务 ID，如腾讯图片异步）；两者均空白时返回 empty
     *
     * @param providerTaskId 供应商侧任务 ID，可为 null（表示按 dataId 反查）
     * @param dataId         业务侧数据唯一标识，可为 null（表示不约束）
     * @return 命中的任务；不存在时返回 {@link Optional#empty()}
     */
    Optional<AuditTask> findByRef(String providerTaskId, String dataId);

    /**
     * 按场景 + 业务 dataId 查找活跃（非终态）任务，用于 submitAsync 幂等去重：
     * 调用方显式传入 dataId 重复提交时复用既有任务，避免云侧产生重复计费任务。
     * 只匹配 status == PENDING 的任务，已终态任务不参与匹配（业务重新提交应创建新任务）
     *
     * <p>默认返回 empty 表示实现不支持按 dataId 查找，幂等去重随之失效（不影响正确性，
     * 只影响成本）。生产实现（DB/Redis）建议覆盖本方法。尽力而为语义：查重与保存之间
     * 无事务边界，极端并发下同一 dataId 仍可能重复提交
     *
     * @param sceneCode 场景 code，非空
     * @param dataId    业务侧数据唯一标识，调用方保证非空白
     * @return 命中的活跃任务；不存在或不支持时返回 {@link Optional#empty()}
     */
    default Optional<AuditTask> findActiveByDataId(String sceneCode, String dataId) {
        return Optional.empty();
    }

    /**
     * 终态迁移（CAS 语义）：仅非终态任务可迁移，迁移成功返回 true；
     * 已终态或任务不存在返回 false。并发调用同一任务时仅首个迁移返回 true，
     * 后续调用返回 false，实现须保证该判定原子
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
