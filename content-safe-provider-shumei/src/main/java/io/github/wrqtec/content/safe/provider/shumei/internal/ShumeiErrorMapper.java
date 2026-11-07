package io.github.wrqtec.content.safe.provider.shumei.internal;

import java.util.regex.Pattern;

import io.github.wrqtec.content.safe.api.exception.ProviderCallException;

/**
 * 数美错误映射：返回码与底层异常 → {@link ProviderCallException}
 *
 * <p>返回码语义（智能文本/图片识别产品 API 文档 V4，github.com/ishumei/api-doc，2026-11-06 调研）：
 * 1100 成功；1901 QPS 超限；1902 参数不合法；1903 服务失败；
 * 1905 文本字数超限；1911 图片下载失败；9101 无权限操作。
 *
 * @author L.David
 */
public final class ShumeiErrorMapper {

    /** 业务成功返回码 */
    public static final int CODE_SUCCESS = 1100;

    /** 长 token 掩码（accessKey 等签名材料） */
    private static final Pattern LONG_TOKEN = Pattern.compile("[A-Za-z0-9+/=_\\-.]{16,}");

    /** message 截断长度 */
    private static final int MAX_MESSAGE_LENGTH = 120;

    /** 同步检测操作标签（错误消息用） */
    private static final String OP_DETECT = "detect";

    private ShumeiErrorMapper() {
    }

    /**
     * 业务响应断言：返回码非 1100 → 抛 {@link ProviderCallException}
     *
     * @param response 已解析响应
     * @return 原响应（链式调用）
     */
    public static ShumeiSyncResponse assertSuccess(ShumeiSyncResponse response) {
        return assertSuccess(response, OP_DETECT);
    }

    /**
     * 业务响应断言（带操作标签）：返回码非 1100 → 抛 {@link ProviderCallException}
     *
     * @param response 已解析响应
     * @param op 操作标签（如 detect / async submit）
     * @return 原响应（链式调用）
     */
    public static ShumeiSyncResponse assertSuccess(ShumeiSyncResponse response, String op) {
        if (response.isSuccess()) {
            return response;
        }
        throw new ProviderCallException(
                "shumei " + op + " rejected: code=" + response.getCode()
                        + " message=" + sanitize(response.getMessage()));
    }

    /**
     * 异步查询响应断言：返回码非 1100 → 抛 {@link ProviderCallException}
     */
    public static ShumeiAsyncQueryResponse assertSuccess(ShumeiAsyncQueryResponse response, String op) {
        if (response.isSuccess()) {
            return response;
        }
        throw new ProviderCallException(
                "shumei " + op + " rejected: code=" + response.getCode()
                        + " message=" + sanitize(response.getMessage()));
    }

    /**
     * 底层异常 → {@link ProviderCallException}（脱敏）
     */
    public static ProviderCallException fromException(Exception e) {
        return fromException(e, OP_DETECT);
    }

    /**
     * 底层异常 → {@link ProviderCallException}（带操作标签，脱敏）
     */
    public static ProviderCallException fromException(Exception e, String op) {
        return new ProviderCallException(
                "shumei " + op + " call failed: " + e.getClass().getSimpleName() + " " + sanitize(e.getMessage()), e);
    }

    /**
     * 脱敏：长 token 掩码 + 截断
     */
    public static String sanitize(String message) {
        if (message == null) {
            return "";
        }
        String masked = LONG_TOKEN.matcher(message).replaceAll("***");
        return masked.length() <= MAX_MESSAGE_LENGTH ? masked : masked.substring(0, MAX_MESSAGE_LENGTH) + "...";
    }
}
