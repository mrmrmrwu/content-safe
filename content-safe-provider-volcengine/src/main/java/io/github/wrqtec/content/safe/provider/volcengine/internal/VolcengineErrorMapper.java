package io.github.wrqtec.content.safe.provider.volcengine.internal;

import com.volcengine.model.response.ResponseMetadata;
import io.github.wrqtec.content.safe.api.exception.ProviderCallException;
import io.github.wrqtec.content.safe.api.util.ErrorMessages;

/**
 * 火山引擎异常与错误响应统一映射。安全约束（铁律 §9）：异常消息绝不包含
 * ak/sk/session token/签名串/用户内容原文；供应商消息一律脱敏截断
 * （脱敏实现统一收敛在 api {@link ErrorMessages}，本类不维护正则副本）
 *
 * @author L.David
 */
public final class VolcengineErrorMapper {

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
     * 供应商消息脱敏：长 token 掩码 + 截断（委托 api 统一实现）
     *
     * @param message 原始消息，可为 null
     * @return 脱敏后的安全消息
     */
    static String sanitize(String message) {
        return ErrorMessages.sanitize(message);
    }

    private static String nullSafe(String value) {
        return value == null ? "" : value;
    }
}
