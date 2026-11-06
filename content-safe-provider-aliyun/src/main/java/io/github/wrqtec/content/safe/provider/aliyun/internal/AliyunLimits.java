package io.github.wrqtec.content.safe.provider.aliyun.internal;

import io.github.wrqtec.content.safe.model.OperationLimits;

/**
 * 阿里云内容安全 2.0 限额声明集中处
 *
 * <p>数值来源：阿里云官方文档（访问日期 2026-11-06），发布前需复核：
 * 文本检测限制见 https://help.aliyun.com/document_detail/434034.html ；
 * 图片检测限制见 https://help.aliyun.com/document_detail/467828.html
 *
 * @author L.David
 */
public final class AliyunLimits {

    /** 文本上限 600 字，按 UTF-8 最坏 4 字节/字符折算的保守字节上限。TODO 发布前复核精确口径 */
    private static final long TEXT_MAX_CONTENT_BYTES = 2400L;

    /** 文本同步检测默认 QPS。TODO 发布前复核（账户配额相关，默认 100） */
    private static final double TEXT_QPS = 100D;

    /** 图片大小上限 20MB。TODO 发布前复核 */
    private static final long IMAGE_MAX_CONTENT_BYTES = 20L * 1024 * 1024;

    /** 图片总像素上限 2.5 亿。TODO 发布前复核 */
    private static final long IMAGE_MAX_PIXELS = 250_000_000L;

    /** 图片同步检测默认 QPS。TODO 发布前复核（账户配额相关，约 50） */
    private static final double IMAGE_QPS = 50D;

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
}
