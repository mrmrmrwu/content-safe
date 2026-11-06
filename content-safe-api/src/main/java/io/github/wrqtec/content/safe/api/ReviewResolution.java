package io.github.wrqtec.content.safe.api;

import java.time.Instant;

import io.github.wrqtec.content.safe.api.enums.ReviewDecision;
import io.github.wrqtec.content.safe.api.util.Asserts;

/**
 * 人审决议：把机审 {@code REVIEW} 结论落为业务终态（通过/驳回）。
 * 与异步任务解耦：taskId 可空——同步审核产生的 REVIEW 没有本库任务，
 * 决议仅经 {@code ReviewResultSink} 外发；taskId 存在时同步迁移任务终态
 *
 * @author L.David
 */
public record ReviewResolution(
        String scene,
        String dataId,
        String taskId,
        ReviewDecision decision,
        String reviewer,
        String note,
        Instant resolvedAt) {

    public ReviewResolution {
        Asserts.notBlank(scene, "scene");
        Asserts.notBlank(dataId, "dataId");
        Asserts.notNull(decision, "decision");
        taskId = normalize(taskId);
        reviewer = normalize(reviewer);
        note = normalize(note);
        resolvedAt = resolvedAt == null ? Instant.now() : resolvedAt;
    }

    /**
     * 创建决议（resolvedAt 取当前时间）
     *
     * @param scene    业务场景 code
     * @param dataId   机审结果 dataId
     * @param taskId   本库统一任务 ID，可为 null（同步审核场景）
     * @param decision 人审终态决议
     * @param reviewer 复核人标识，可为 null
     * @param note     复核备注，可为 null
     * @return 人审决议
     */
    public static ReviewResolution of(String scene, String dataId, String taskId,
                                      ReviewDecision decision, String reviewer, String note) {
        return new ReviewResolution(scene, dataId, taskId, decision, reviewer, note, null);
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
