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

    /**
     * 异步视频检测的 API 版本（AsyncVideoRisk/VideoResult，与图片同版本）。
     * 来源：官方 Python SDK volcengine/content_security/ContentSecurityService.py（查阅日期 2026-11-11）
     */
    public static final String API_VERSION_VIDEO_RISK = "2021-11-29";

    /** 文本同步检测操作 */
    public static final String OPERATION_TEXT_RISK = "TextRisk";

    /** 图片同步检测操作（V2，V1 已被官方标记为 deprecated） */
    public static final String OPERATION_IMAGE_CONTENT_RISK_V2 = "ImageContentRiskV2";

    /** 异步视频提交操作（提交仅回执，任务关联键为提交时自带的业务 dataId） */
    public static final String OPERATION_ASYNC_VIDEO_RISK = "AsyncVideoRisk";

    /** 异步视频结果查询操作（按 dataId 查询） */
    public static final String OPERATION_VIDEO_RESULT = "VideoResult";

    /** extra 配置键：内容安全控制台申请的应用 ID（整型） */
    public static final String EXTRA_APP_ID = "app-id";

    /** extra 配置键：文本内容在 parameters 内层 JSON 的键名 */
    public static final String EXTRA_TEXT_PARAMETER_KEY = "parameters-text-key";

    /** extra 配置键：图片内容在 parameters 内层 JSON 的键名 */
    public static final String EXTRA_IMAGE_PARAMETER_KEY = "parameters-image-key";

    /** extra 配置键：视频 URL 在 parameters 内层 JSON 的键名 */
    public static final String EXTRA_VIDEO_PARAMETER_KEY = "parameters-video-key";

    /**
     * extra 配置键：视频任务关联标识（dataId）在 parameters 内层 JSON 的键名。
     * 官方未公开内层 schema，提交回执无任务句柄、查询按 dataId 关联，故 dataId
     * 必须随 parameters 提交；键名与所配 Service 文档约定，缺省 dataId
     */
    public static final String EXTRA_VIDEO_DATA_ID_KEY = "parameters-video-data-id-key";

    /** 视频任务关联标识的缺省键名 */
    public static final String DEFAULT_VIDEO_DATA_ID_KEY = "dataId";

    private VolcengineProviderConstants() {
    }
}
