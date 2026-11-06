package io.github.wrqtec.content.safe.model.enums;

/**
 * 异步任务状态机：PENDING 为唯一非终态。
 *
 * @author L.David
 */
public enum TaskStatus {

    /** 待处理（唯一非终态） */
    PENDING,
    /** 审核成功（终态） */
    SUCCEEDED,
    /** 审核失败（终态） */
    FAILED;

    /**
     * 是否为终态。终态任务不允许再次迁移。
     *
     * @return true 表示终态（SUCCEEDED/FAILED）
     */
    public boolean isTerminal() {
        return this != PENDING;
    }
}
