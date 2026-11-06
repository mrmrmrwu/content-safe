package io.github.wrqtec.content.safe.provider.tencent.internal;

import java.util.regex.Pattern;

import com.tencentcloudapi.common.exception.TencentCloudSDKException;
import io.github.wrqtec.content.safe.api.exception.ProviderCallException;

/**
 * 腾讯云异常与错误响应统一映射。安全约束（铁律 §9）：异常消息绝不包含
 * secretId、secretKey、签名串、用户内容原文；供应商消息一律脱敏截断
 *
 * @author L.David
 */
public final class TencentErrorMapper {

    /** 供应商消息最大保留长度 */
    private static final int MAX_VENDOR_MESSAGE_LENGTH = 120;

    /** 长 token（疑似密钥/签名串）掩码 */
    private static final Pattern TOKEN_PATTERN = Pattern.compile("[A-Za-z0-9+/=_-]{16,}");

    /** 掩码占位 */
    private static final String MASK = "***";

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
     * 供应商消息脱敏：长 token 掩码 + 截断
     *
     * @param message 原始消息，可为 null
     * @return 脱敏后的安全消息
     */
    static String sanitize(String message) {
        if (message == null || message.isBlank()) {
            return "";
        }
        String masked = TOKEN_PATTERN.matcher(message).replaceAll(MASK);
        if (masked.length() > MAX_VENDOR_MESSAGE_LENGTH) {
            return masked.substring(0, MAX_VENDOR_MESSAGE_LENGTH) + "...";
        }
        return masked;
    }
}
