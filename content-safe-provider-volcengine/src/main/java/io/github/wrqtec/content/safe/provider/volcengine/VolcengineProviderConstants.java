package io.github.wrqtec.content.safe.provider.volcengine;

/**
 * 火山引擎内容安全提供者常量。接口契约来源：官方 SDK
 * {@code com.volcengine:volc-sdk-java:1.0.290} 与官方 Python SDK
 * volcengine/content_security/ContentSecurityService.py（查阅日期 2026-11-06）
 *
 * @author L.David
 */
public final class VolcengineProviderConstants {

    /** 提供者标识 */
    public static final String PROVIDER = "volcengine";

    /** 产品标识（内容安全/风控产品线，host riskcontrol.volcengineapi.com） */
    public static final String PRODUCT = "volcengine-content-security";

    /** 文本检测 TextRisk 的 API 版本 */
    public static final String API_VERSION_TEXT_RISK = "2022-01-26";

    /** 图片内容检测 ImageContentRiskV2 的 API 版本 */
    public static final String API_VERSION_IMAGE_RISK = "2021-11-29";

    /** V4 签名 service 名（内容安全产品线约定） */
    public static final String SIGN_SERVICE = "BusinessSecurity";

    /** 签名 region */
    public static final String SIGN_REGION = "cn-north-1";

    /** 文本同步检测操作 */
    public static final String OPERATION_TEXT_RISK = "TextRisk";

    /** 图片同步检测操作（V2，V1 已被官方标记为 deprecated） */
    public static final String OPERATION_IMAGE_CONTENT_RISK_V2 = "ImageContentRiskV2";

    /** extra 配置键：内容安全控制台申请的应用 ID（整型） */
    public static final String EXTRA_APP_ID = "app-id";

    /** extra 配置键：文本内容在 parameters 内层 JSON 的键名 */
    public static final String EXTRA_TEXT_PARAMETER_KEY = "parameters-text-key";

    /** extra 配置键：图片内容在 parameters 内层 JSON 的键名 */
    public static final String EXTRA_IMAGE_PARAMETER_KEY = "parameters-image-key";

    private VolcengineProviderConstants() {
    }
}
