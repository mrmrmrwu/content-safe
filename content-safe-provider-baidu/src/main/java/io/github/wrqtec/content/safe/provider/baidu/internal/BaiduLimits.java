package io.github.wrqtec.content.safe.provider.baidu.internal;

import java.time.Duration;

import io.github.wrqtec.content.safe.model.OperationLimits;

/**
 * 百度内容审核操作限额（用于本地限流与请求前置校验）
 *
 * <p>TODO: 复核限额与百度最新文档一致（https://cloud.baidu.com/doc/ANTIPORN，2026-11-06 调研）
 *
 * @author L.David
 */
public final class BaiduLimits {

    /** 文本上限：20000 字节（UTF-8） */
    public static final long MAX_TEXT_BYTES = 20_000L;

    /** 图片上限：base64 后 4MB */
    public static final long MAX_IMAGE_BYTES = 4L * 1024 * 1024;

    /** 文本限流 QPS（付费后 200） */
    public static final double TEXT_QPS = 200D;

    /** 图像限流 QPS（付费后 50） */
    public static final double IMAGE_QPS = 50D;

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

    public static long maxTextBytes() {
        return MAX_TEXT_BYTES;
    }

    public static long maxImageBytes() {
        return MAX_IMAGE_BYTES;
    }
}
