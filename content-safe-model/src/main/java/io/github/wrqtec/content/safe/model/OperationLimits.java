package io.github.wrqtec.content.safe.model;

import java.time.Duration;

/**
 * 供应商操作的限额声明。不可变；各字段为 null/0 表示未声明（不作限制）。
 *
 * @param maxConcurrency     最大并发
 * @param qps                每秒请求上限
 * @param timeout            调用超时
 * @param maxContentBytes    单条内容大小上限（字节）
 * @param maxPixels          图片像素上限
 * @param multiSceneBilling  多场景是否重复计费（成本路由依据之一）
 * @author L.David
 */
public record OperationLimits(
        Integer maxConcurrency,
        Double qps,
        Duration timeout,
        long maxContentBytes,
        long maxPixels,
        boolean multiSceneBilling) {

    /**
     * 未声明任何限额。
     */
    public static OperationLimits unlimited() {
        return new OperationLimits(null, null, null, 0L, 0L, false);
    }
}
