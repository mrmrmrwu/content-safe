package io.github.wrqtec.content.safe.provider.aliyun.internal;

import com.aliyun.tea.TeaException;
import io.github.wrqtec.content.safe.api.exception.ProviderCallException;
import io.github.wrqtec.content.safe.api.util.ErrorMessages;

/**
 * 阿里云异常与错误响应统一映射。安全约束（铁律 §9）：异常消息绝不包含
 * accessKey、secret、签名串、用户内容原文；供应商消息一律脱敏截断
 * （脱敏实现统一收敛在 api {@link ErrorMessages}，本类不维护正则副本）
 *
 * @author L.David
 */
public final class AliyunErrorMapper {

    /** 业务成功码 */
    public static final int SUCCESS_CODE = 200;

    private AliyunErrorMapper() {
    }

    /**
     * 检查业务错误码：非 200 抛 {@link ProviderCallException}
     *
     * @param code          响应体 code
     * @param vendorMessage 供应商消息（脱敏后进入异常消息）
     * @throws ProviderCallException 业务错误码非 200 时抛出
     */
    public static void assertSuccess(Integer code, String vendorMessage) {
        if (code != null && code == SUCCESS_CODE) {
            return;
        }
        throw new ProviderCallException("aliyun green returned error code=" + code
                + " msg=" + sanitize(vendorMessage));
    }

    /**
     * 调用异常映射为 {@link ProviderCallException}
     *
     * @param e SDK/网络异常
     * @return 映射后的受控异常
     */
    public static ProviderCallException fromException(Exception e) {
        if (e instanceof TeaException teaException) {
            // TeaException.message 可能携带签名材料，只保留 code 与 HTTP 状态码
            return new ProviderCallException("aliyun green call failed: code=" + teaException.getCode()
                    + " status=" + teaException.getStatusCode(), e);
        }
        return new ProviderCallException("aliyun green call failed: " + e.getClass().getSimpleName()
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
