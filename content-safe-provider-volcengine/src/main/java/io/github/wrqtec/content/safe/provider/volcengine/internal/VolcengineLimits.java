package io.github.wrqtec.content.safe.provider.volcengine.internal;

import java.time.Duration;

import io.github.wrqtec.content.safe.model.OperationLimits;

/**
 * 火山引擎内容安全限额声明。官方公开文档未给出确定的 QPS/大小配额
 * （配额随账号与服务配置变化），此处仅声明可从接口形态确证的事实：
 * 单次调用携带单条内容（maxBatchSize=1，envelope 无批量字段）、
 * SDK ServiceInfo 默认超时 connect/socket 各 5 秒。其余配额以控制台为准
 *
 * @author L.David
 */
public final class VolcengineLimits {

    /** SDK ServiceInfo 默认 socket 超时（秒） */
    private static final long DEFAULT_TIMEOUT_SECONDS = 5L;

    private VolcengineLimits() {
    }

    /**
     * 文本检测限额
     */
    public static OperationLimits textRisk() {
        return new OperationLimits(null, null, Duration.ofSeconds(DEFAULT_TIMEOUT_SECONDS),
                0L, 0L, false);
    }

    /**
     * 图片检测限额
     */
    public static OperationLimits imageContentRisk() {
        return new OperationLimits(null, null, Duration.ofSeconds(DEFAULT_TIMEOUT_SECONDS),
                0L, 0L, false);
    }

    /**
     * 异步视频提交/查询限额（查询结果保留期与轮询间隔官方未公开确定值，
     * 以控制台为准）
     */
    public static OperationLimits videoAsync() {
        return new OperationLimits(null, null, Duration.ofSeconds(DEFAULT_TIMEOUT_SECONDS),
                0L, 0L, false);
    }
}
