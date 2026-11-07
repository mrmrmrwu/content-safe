package io.github.wrqtec.content.safe.api.exception;

/**
 * 对外稳定错误码，新增错误码必须先更新错误码表文档（docs），再写代码
 *
 * @author L.David
 */
public final class ErrorCodes {

    /** 请求参数非法 */
    public static final String INVALID_REQUEST = "INVALID_REQUEST";

    /** 业务场景未配置或未映射到供应商场景 */
    public static final String SCENE_NOT_CONFIGURED = "SCENE_NOT_CONFIGURED";

    /** 内容类型/引用方式超出供应商能力 */
    public static final String UNSUPPORTED_CONTENT = "UNSUPPORTED_CONTENT";

    /** 配置了未实现的特性（如 v1 的 video） */
    public static final String UNSUPPORTED_FEATURE = "UNSUPPORTED_FEATURE";

    /** 无可用供应商（熔断、未配置、健康 DOWN） */
    public static final String PROVIDER_NOT_AVAILABLE = "PROVIDER_NOT_AVAILABLE";

    /** 供应商调用失败（超时、限流、5xx、鉴权失败） */
    public static final String PROVIDER_CALL_ERROR = "PROVIDER_CALL_ERROR";

    /** 回调验签失败 */
    public static final String CALLBACK_VERIFY_FAILED = "CALLBACK_VERIFY_FAILED";

    /** 路由链上全部供应商失败 */
    public static final String ALL_PROVIDER_FAILED = "ALL_PROVIDER_FAILED";

    /** 启动期配置非法或无效（错误码表见 docs/error-code-table.md） */
    public static final String CONFIG_INVALID = "CONFIG_INVALID";

    private ErrorCodes() {
    }
}
