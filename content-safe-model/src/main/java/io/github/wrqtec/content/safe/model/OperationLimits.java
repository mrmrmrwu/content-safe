package io.github.wrqtec.content.safe.model;

import java.time.Duration;

/**
 * 供应商操作的限额声明。不可变；各字段为 null/0 表示未声明（不作限制）。
 * core 编排层仅消费 {@code qps}/{@code maxConcurrency} 做限流；
 * {@code timeout}/{@code maxContentBytes}/{@code maxPixels} 是声明值
 * （供能力矩阵展示与调用方参考，实际超时由各 provider 的 SDK/HTTP 客户端控制），
 * {@code multiSceneBilling} 预留给成本路由
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
     * 未声明任何限额
     */
    public static OperationLimits unlimited() {
        return new OperationLimits(null, null, null, 0L, 0L, false);
    }
}
