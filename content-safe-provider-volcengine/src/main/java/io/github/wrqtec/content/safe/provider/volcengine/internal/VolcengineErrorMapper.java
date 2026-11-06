package io.github.wrqtec.content.safe.provider.volcengine.internal;

import java.util.regex.Pattern;

import com.volcengine.model.response.ResponseMetadata;
import io.github.wrqtec.content.safe.api.exception.ProviderCallException;

/**
 * 火山引擎异常与错误响应统一映射。安全约束（铁律 §9）：异常消息绝不包含
 * ak/sk/session token/签名串/用户内容原文；供应商消息一律脱敏截断
 *
 * @author L.David
 */
public final class VolcengineErrorMapper {

    /** 供应商消息最大保留长度 */
    private static final int MAX_VENDOR_MESSAGE_LENGTH = 120;

    /** 长 token（疑似密钥/签名串）掩码 */
    private static final Pattern TOKEN_PATTERN = Pattern.compile("[A-Za-z0-9+/=_-]{16,}");

    /** 掩码占位 */
    private static final String MASK = "***";

    private VolcengineErrorMapper() {
    }

    /**
     * 调用异常映射为 {@link ProviderCallException}
     *
     * @param e SDK/网络异常
     * @return 映射后的受控异常
     */
    public static ProviderCallException fromException(Exception e) {
        return new ProviderCallException("volcengine content security call failed: "
                + e.getClass().getSimpleName() + " " + sanitize(e.getMessage()), e);
    }

    /**
     * 响应元数据错误映射为 {@link ProviderCallException}
     *
     * @param error     响应元数据中的错误对象
     * @param requestId 响应 requestId，可为 null
     * @return 映射后的受控异常
     */
    public static ProviderCallException fromResponseError(ResponseMetadata.Error error, String requestId) {
        return new ProviderCallException("volcengine content security call failed: code="
                + nullSafe(error.getCode()) + " requestId=" + nullSafe(requestId)
                + " message=" + sanitize(error.getMessage()));
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

    private static String nullSafe(String value) {
        return value == null ? "" : value;
    }
}
