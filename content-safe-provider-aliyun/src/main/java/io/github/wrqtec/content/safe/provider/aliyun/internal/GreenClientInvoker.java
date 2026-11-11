package io.github.wrqtec.content.safe.provider.aliyun.internal;

import java.util.Map;

import com.aliyun.green20220302.models.DescribeImageModerationResultResponseBody;
import com.aliyun.green20220302.models.ImageAsyncModerationResponseBody;
import com.aliyun.green20220302.models.ImageModerationResponseBody;
import com.aliyun.green20220302.models.TextModerationResponseBody;
import com.aliyun.green20220302.models.VideoModerationResultResponseBody;
import com.aliyun.green20220302.models.VideoModerationResponseBody;

/**
 * Green SDK 调用接缝：隔离 SDK Client，便于用 stub 做单元测试（禁止真实云调用）。
 * SDK 类型只允许出现在 internal 包内
 *
 * @author L.David
 */
public interface GreenClientInvoker {

    /**
     * 文本同步检测（TextModeration）
     *
     * @param service           阿里云 serviceCode
     * @param serviceParameters 业务参数（content / dataId），值不得为 null
     * @return SDK 响应体
     * @throws Exception 网络/限流/鉴权等调用异常原样上抛，由调用方统一映射
     */
    TextModerationResponseBody textModeration(String service, Map<String, String> serviceParameters)
            throws Exception;

    /**
     * 图片同步检测（ImageModeration）
     *
     * @param service           阿里云 serviceCode
     * @param serviceParameters 业务参数（imageUrl / dataId），值不得为 null
     * @return SDK 响应体
     * @throws Exception 网络/限流/鉴权等调用异常原样上抛，由调用方统一映射
     */
    ImageModerationResponseBody imageModeration(String service, Map<String, String> serviceParameters)
            throws Exception;

    /**
     * 图片异步检测提交（ImageAsyncModeration）
     *
     * @param service           阿里云 serviceCode
     * @param serviceParameters 业务参数（imageUrl / dataId / callback / seed 等），值不得为 null
     * @return SDK 响应体（data.reqId 为任务引用）
     * @throws Exception 网络/限流/鉴权等调用异常原样上抛，由调用方统一映射
     */
    ImageAsyncModerationResponseBody imageAsyncModeration(String service, Map<String, String> serviceParameters)
            throws Exception;

    /**
     * 图片异步检测结果查询（DescribeImageModerationResult，按 reqId 轮询，结果保留 3 天）
     *
     * @param reqId 提交时返回的请求 ID
     * @return SDK 响应体（code=280 表示任务检测中）
     * @throws Exception 网络/限流/鉴权等调用异常原样上抛，由调用方统一映射
     */
    DescribeImageModerationResultResponseBody describeImageModerationResult(String reqId) throws Exception;

    /**
     * 视频文件审核增强版提交（VideoModeration）
     *
     * @param service           阿里云 Service（官方固定值 videoDetection）
     * @param serviceParameters 业务参数（url / dataId），值不得为 null
     * @return SDK 响应体（data.taskId 为任务引用）
     * @throws Exception 网络/限流/鉴权等调用异常原样上抛，由调用方统一映射
     */
    VideoModerationResponseBody videoModeration(String service, Map<String, String> serviceParameters)
            throws Exception;

    /**
     * 视频文件审核增强版结果查询（VideoModerationResult，按 taskId 轮询，结果保留 24 小时）
     *
     * @param service           阿里云 Service（与提交一致）
     * @param serviceParameters 业务参数（taskId），值不得为 null
     * @return SDK 响应体（code=200 完成、280 检测中、288 排队中）
     * @throws Exception 网络/限流/鉴权等调用异常原样上抛，由调用方统一映射
     */
    VideoModerationResultResponseBody videoModerationResult(String service, Map<String, String> serviceParameters)
            throws Exception;
}
