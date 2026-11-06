package io.github.wrqtec.content.safe.provider.aliyun.internal;

import java.util.Map;

import com.aliyun.green20220302.models.ImageModerationResponseBody;
import com.aliyun.green20220302.models.TextModerationResponseBody;

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
}
