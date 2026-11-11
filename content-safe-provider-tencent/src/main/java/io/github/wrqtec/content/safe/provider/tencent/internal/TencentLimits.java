package io.github.wrqtec.content.safe.provider.tencent.internal;

import java.time.Duration;

import io.github.wrqtec.content.safe.model.OperationLimits;

/**
 * 腾讯云内容安全限额（TMS/IMS）。数值来源：腾讯云文档
 * （文本 10000 Unicode 字符、QPS 1000；图片 Base64 ≤10MB、URL 源图 ≤30MB、QPS 100，
 * 访问日期 2026-11-06）；配额随账号可调整，以控制台为准
 *
 * @author L.David
 */
public final class TencentLimits {

    /** 文本最大字符数（Unicode 字符） */
    private static final int MAX_TEXT_CHARS = 10000;

    /** 文本默认 QPS */
    private static final double TEXT_QPS = 1000.0;

    /** 图片 Base64（FileContent）最大字节数 */
    private static final long MAX_BASE64_BYTES = 10L * 1024 * 1024;

    /** 图片 URL（FileUrl）源图最大字节数 */
    private static final long MAX_URL_BYTES = 30L * 1024 * 1024;

    /** 图片默认 QPS */
    private static final double IMAGE_QPS = 100.0;

    /** 图片异步任务创建默认 QPS（官方限频 20，查阅 2026-11-07） */
    private static final double IMAGE_ASYNC_QPS = 20.0;

    /** 默认超时 */
    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(10);

    private TencentLimits() {
    }

    /**
     * 文本同步操作限额
     */
    public static OperationLimits textSync() {
        return new OperationLimits(null, TEXT_QPS, DEFAULT_TIMEOUT,
                MAX_TEXT_CHARS, 0, false);
    }

    /**
     * 图片同步操作限额
     */
    public static OperationLimits imageSync() {
        return new OperationLimits(null, IMAGE_QPS, DEFAULT_TIMEOUT,
                MAX_BASE64_BYTES, 0, false);
    }

    /**
     * 图片异步任务创建限额（结果推送无主动查询流量）
     */
    public static OperationLimits imageAsync() {
        return new OperationLimits(null, IMAGE_ASYNC_QPS, DEFAULT_TIMEOUT,
                MAX_BASE64_BYTES, 0, false);
    }

    /**
     * 视频异步审核限额。官方未公示并发上限（任务提交后云侧排队执行，
     * 查询接口 DescribeTaskDetail 建议轮询间隔见官方文档），本地不限流，
     * 仅超时兜底（查阅 2026-11-11）
     */
    public static OperationLimits videoAsync() {
        return new OperationLimits(null, null, DEFAULT_TIMEOUT,
                0, 0, false);
    }

    /**
     * 文本最大字符数（供超限校验）
     */
    public static int maxTextChars() {
        return MAX_TEXT_CHARS;
    }

    /**
     * 图片 Base64 最大字节数（供超限校验）
     */
    public static long maxBase64Bytes() {
        return MAX_BASE64_BYTES;
    }

    /**
     * 图片 URL 源图最大字节数（能力声明用；URL 内容长度调用前不可预知，仅声明）
     */
    public static long maxUrlBytes() {
        return MAX_URL_BYTES;
    }
}
