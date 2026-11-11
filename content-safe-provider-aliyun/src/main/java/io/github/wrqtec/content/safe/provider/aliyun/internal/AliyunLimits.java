package io.github.wrqtec.content.safe.provider.aliyun.internal;

import java.time.Duration;

import io.github.wrqtec.content.safe.model.OperationLimits;

/**
 * 阿里云内容安全 2.0 限额声明集中处
 *
 * <p>数值来源（官方文档，查阅日期 2026-11-07）：
 * 文本审核增强版通用服务 API 单用户 QPS 100 次/秒、文本输入限 600 字
 * （https://help.aliyun.com/zh/document_detail/434034.html、2671445）；
 * 图片限 20MB、单边 ≤30000px、总像素 ≤2.5 亿
 * （https://help.aliyun.com/zh/document_detail/70292.html）；
 * 图片审核增强版 ImageModeration 单用户 QPS 100 次/秒
 * （https://help.aliyun.com/zh/document_detail/2528742.html）。
 * QPS 为本地保护性限流缺省，实际以账户配额为准
 *
 * @author L.David
 */
public final class AliyunLimits {

    /** 文本上限 600 字，按 UTF-8 最坏 4 字节/字符折算的保守字节上限 */
    private static final long TEXT_MAX_CONTENT_BYTES = 2400L;

    /** 文本同步检测 QPS（官方缺省 100 次/秒） */
    private static final double TEXT_QPS = 100D;

    /** 图片大小上限 20MB */
    private static final long IMAGE_MAX_CONTENT_BYTES = 20L * 1024 * 1024;

    /** 图片总像素上限 2.5 亿（单边另限 30000px，本库不裁剪仅声明像素口径） */
    private static final long IMAGE_MAX_PIXELS = 250_000_000L;

    /** 图片同步检测 QPS（图片审核增强版 ImageModeration 官方缺省 100 次/秒） */
    private static final double IMAGE_QPS = 100D;

    /** 图片异步提交 QPS（ImageAsyncModeration 官方缺省 100 次/秒） */
    private static final double IMAGE_ASYNC_QPS = 100D;

    private AliyunLimits() {
    }

    /**
     * 文本同步审核限额
     *
     * @return 限额声明
     */
    public static OperationLimits textSync() {
        return new OperationLimits(null, TEXT_QPS, null, TEXT_MAX_CONTENT_BYTES, 0L, false);
    }

    /**
     * 图片同步审核限额
     *
     * @return 限额声明
     */
    public static OperationLimits imageSync() {
        return new OperationLimits(null, IMAGE_QPS, null, IMAGE_MAX_CONTENT_BYTES, IMAGE_MAX_PIXELS, false);
    }

    /**
     * 图片异步提交限额（轮询 DescribeImageModerationResult 不计费不设限）
     *
     * @return 限额声明
     */
    public static OperationLimits imageAsync() {
        return new OperationLimits(null, IMAGE_ASYNC_QPS, null, IMAGE_MAX_CONTENT_BYTES, 0L, false);
    }

    /**
     * 视频文件审核增强版限额。官方未公示视频提交/查询的默认 QPS 与并发
     * （提交后任务进云侧排队，本地不设保护性限流）；提交与查询接口调用超时 10 秒。
     * 内容为 URL（视频文件本体 ≤500MB 由云侧校验），本地无字节约束
     * （来源：help.aliyun.com/zh/document_detail/2505810，查阅 2026-11-11）
     *
     * @return 限额声明
     */
    public static OperationLimits videoModeration() {
        return new OperationLimits(null, null, Duration.ofSeconds(10), 0L, 0L, false);
    }
}
