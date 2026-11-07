package io.github.wrqtec.content.safe.provider.shumei.internal;

import java.time.Duration;

import io.github.wrqtec.content.safe.model.OperationLimits;

/**
 * 数美内容安全操作限额（用于请求前置校验）
 *
 * <p>官方同步接口建议超时 1s；此处取 5s 余量，QPS 以与数美合同约定为准（不本地声明）。
 * 文本单次请求字符数上限 1 万字、请求体上限 1MB（字数在操作内校验）。
 *
 * @author L.David
 */
public final class ShumeiLimits {

    /** 文本字符数上限（官方：单次请求 1 万字） */
    public static final long MAX_TEXT_CHARS = 10_000L;

    /** 请求体上限：1MB（data 对象） */
    public static final long MAX_BODY_BYTES = 1L * 1024 * 1024;

    /** 默认超时（官方建议 1s，取 5s 余量） */
    public static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(5);

    private ShumeiLimits() {
    }

    public static OperationLimits textSync() {
        return new OperationLimits(null, null, DEFAULT_TIMEOUT, 0L, 0L, false);
    }

    public static OperationLimits imageSync() {
        return new OperationLimits(null, null, DEFAULT_TIMEOUT, 0L, 0L, false);
    }

    /**
     * 图片异步提交/查询（官方建议超时 5s；QPS 以与数美合同约定为准）
     */
    public static OperationLimits imageAsync() {
        return new OperationLimits(null, null, DEFAULT_TIMEOUT, 0L, 0L, false);
    }

    public static long maxTextChars() {
        return MAX_TEXT_CHARS;
    }
}
