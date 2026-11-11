package io.github.wrqtec.content.safe.provider.baidu.internal;

import java.util.Set;

import io.github.wrqtec.content.safe.api.exception.ProviderCallException;
import io.github.wrqtec.content.safe.api.util.ErrorMessages;
import io.github.wrqtec.content.safe.provider.baidu.BaiduProviderConstants;

/**
 * 百度错误映射：error_code 与异常 → {@link ProviderCallException}
 *
 * <p>错误码语义（https://cloud.baidu.com/doc/ANTIPORN，2026-11-06 调研）：
 * <ul>
 *   <li>18 Open api qps request limit reached / 19 Open api total request limit reached：限流，对端可重试</li>
 *   <li>110 Access token invalid / 111 Access token expired：token 问题，由客户端失效重取重试一次</li>
 * </ul>
 *
 * @author L.David
 */
public final class BaiduErrorMapper {

    /** 对端限流错误码（18/19），建议调用方退避重试 */
    private static final Set<Integer> THROTTLED_CODES = Set.of(18, 19);

    /** token 无效/过期错误码（触发 token 失效重取） */
    private static final Set<Integer> TOKEN_CODES = Set.of(110, 111);

    private BaiduErrorMapper() {
    }

    /**
     * token 类错误码（110/111）
     */
    public static boolean isTokenError(Integer errorCode) {
        return errorCode != null && TOKEN_CODES.contains(errorCode);
    }

    /**
     * 业务响应断言：error_code 非空 → 抛 {@link ProviderCallException}
     *
     * @param response 已解析响应
     * @return 原响应（链式调用）
     */
    public static BaiduCensorResponse assertSuccess(BaiduCensorResponse response) {
        if (response.isSuccess()) {
            return response;
        }
        throw new ProviderCallException(
                "baidu censor rejected: error_code=" + response.getErrorCode()
                        + " error_msg=" + sanitize(response.getErrorMsg()));
    }

    /**
     * 长视频提交断言：ret 非 "0" → 抛 {@link ProviderCallException}
     * （如 282005 appId+extId 重复提交、282006 任务不存在、282003/282004 参数问题）
     *
     * @param response 已解析提交响应
     * @return 原响应（链式调用）
     */
    public static BaiduVideoSubmitResponse assertVideoSuccess(BaiduVideoSubmitResponse response) {
        if (response.isSuccess()) {
            return response;
        }
        throw new ProviderCallException(
                "baidu video rejected: ret=" + response.getRet() + " msg=" + sanitize(response.getMsg()));
    }

    /**
     * 长视频拉取断言：ret 非 "0" → 抛 {@link ProviderCallException}
     *
     * @param response 已解析拉取响应
     * @return 原响应（链式调用）
     */
    public static BaiduVideoPullResponse assertVideoSuccess(BaiduVideoPullResponse response) {
        if (response.isSuccess()) {
            return response;
        }
        throw new ProviderCallException(
                "baidu video rejected: ret=" + response.getRet() + " msg=" + sanitize(response.getMsg()));
    }

    /**
     * 底层异常 → {@link ProviderCallException}（脱敏）
     */
    public static ProviderCallException fromException(Exception e) {
        return new ProviderCallException(
                "baidu censor call failed: " + e.getClass().getSimpleName() + " " + sanitize(e.getMessage()), e);
    }

    /**
     * 脱敏：长 token 掩码 + 截断（委托 api 统一实现，本类不维护正则副本）
     */
    public static String sanitize(String message) {
        return ErrorMessages.sanitize(message);
    }
}
