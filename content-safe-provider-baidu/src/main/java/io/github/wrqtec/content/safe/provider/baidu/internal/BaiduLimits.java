package io.github.wrqtec.content.safe.provider.baidu.internal;

import java.time.Duration;

import io.github.wrqtec.content.safe.model.OperationLimits;

/**
 * 百度内容审核操作限额（用于本地限流与请求前置校验）
 *
 * <p>数值来源（官方文档，查阅日期 2026-11-07）：
 * 文本长度限 20000 字节、图片 base64 编码后 &lt;4MB（最短边 ≥64px、最长边 &lt;8192px）
 * （内容审核平台请求说明 https://cloud.baidu.com/doc/ANTIPORN/s/Rk3h6xb3i）。
 * QPS 官方未公开统一缺省值（免费版 5 QPS，付费后按并发资源包扩展），
 * 下列 QPS 为本地保护性限流缺省，实际以控制台配额为准
 *
 * @author L.David
 */
public final class BaiduLimits {

    /** 文本上限：20000 字节（UTF-8） */
    public static final long MAX_TEXT_BYTES = 20_000L;

    /** 图片上限：base64 后 4MB */
    public static final long MAX_IMAGE_BYTES = 4L * 1024 * 1024;

    /** 文本限流 QPS（本地保护性缺省，以控制台配额为准） */
    public static final double TEXT_QPS = 200D;

    /** 图像限流 QPS（本地保护性缺省，以控制台配额为准） */
    public static final double IMAGE_QPS = 50D;

    /**
     * 长视频限流 QPS（本地保护性缺省）。提交/拉取是轻量 REST 调用，
     * 视频时长与体积限制在云侧校验（≤3h/≤2G 建议值，jksy7j3jv 查阅 2026-11-11），
     * 拉取建议间隔 30s（不小于 20s）
     */
    public static final double VIDEO_QPS = 10D;

    /** 默认超时（HTTP 同步接口，10s 足够） */
    public static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(10);

    private BaiduLimits() {
    }

    public static OperationLimits textSync() {
        return new OperationLimits(null, TEXT_QPS, DEFAULT_TIMEOUT, MAX_TEXT_BYTES, 0L, false);
    }

    public static OperationLimits imageSync() {
        return new OperationLimits(null, IMAGE_QPS, DEFAULT_TIMEOUT, MAX_IMAGE_BYTES, 0L, false);
    }

    public static OperationLimits videoAsync() {
        return new OperationLimits(null, VIDEO_QPS, DEFAULT_TIMEOUT, 0L, 0L, false);
    }

    public static long maxTextBytes() {
        return MAX_TEXT_BYTES;
    }

    public static long maxImageBytes() {
        return MAX_IMAGE_BYTES;
    }
}
