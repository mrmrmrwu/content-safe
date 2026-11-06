package io.github.wrqtec.content.safe.spi;

import io.github.wrqtec.content.safe.api.ReviewResolution;

/**
 * 人审终态回写出口。业务方实现后注入，starter 按 0..1 装配；
 * {@code ContentSafeTemplate#resolveReview} 在异步任务终态迁移前调用，
 * 实现抛出的异常会中断决议流程（先于状态变更暴露，业务可整体重试）。
 * 实现方负责持久化/通知下游，必须同步返回，异步处理自行入队
 *
 * @author L.David
 */
public interface ReviewResultSink {

    /**
     * 回写人审终态
     *
     * @param resolution 人审决议，不为 null
     */
    void resolve(ReviewResolution resolution);
}
