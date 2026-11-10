package io.github.wrqtec.content.safe.provider.tencent.internal;

import com.tencentcloudapi.common.exception.TencentCloudSDKException;
import io.github.wrqtec.content.safe.api.exception.ProviderCallException;
import io.github.wrqtec.content.safe.api.util.ErrorMessages;

/**
 * 腾讯云异常与错误响应统一映射。安全约束（铁律 §9）：异常消息绝不包含
 * secretId、secretKey、签名串、用户内容原文；供应商消息一律脱敏截断
 * （脱敏实现统一收敛在 api {@link ErrorMessages}，本类不维护正则副本）
 *
 * @author L.David
 */
public final class TencentErrorMapper {

    private TencentErrorMapper() {
    }

    /**
     * 调用异常映射为 {@link ProviderCallException}
     *
     * @param e SDK/网络异常
     * @return 映射后的受控异常
     */
    public static ProviderCallException fromException(Exception e) {
        if (e instanceof TencentCloudSDKException sdkException) {
            // SDK 异常 message 可能携带签名材料，只保留 errorCode 与 requestId
            return new ProviderCallException("tencent cms call failed: errorCode="
                    + sdkException.getErrorCode() + " requestId=" + sdkException.getRequestId(), e);
        }
        return new ProviderCallException("tencent cms call failed: " + e.getClass().getSimpleName()
                + " " + sanitize(e.getMessage()), e);
    }

    /**
     * 供应商消息脱敏：长 token 掩码 + 截断（委托 api 统一实现）
     *
     * @param message 原始消息，可为 null
     * @return 脱敏后的安全消息
     */
    static String sanitize(String message) {
        return ErrorMessages.sanitize(message);
    }
}
