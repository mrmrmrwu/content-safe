package io.github.wrqtec.content.safe.provider.shumei;

/**
 * 数美内容安全常量
 *
 * @author L.David
 */
public final class ShumeiProviderConstants {

    /** 供应商标识 */
    public static final String PROVIDER = "shumei";

    /** 产品线：智能内容安全 */
    public static final String PRODUCT = "shumei-content-security";

    /** 接口版本 */
    public static final String API_VERSION = "v4";

    /** 业务成功返回码（文本/图片同步与异步查询共用，官方文档 2026-11-06 调研） */
    public static final int CODE_SUCCESS = 1100;

    /** 文本同步检测操作名 */
    public static final String OPERATION_TEXT_DETECT = "TextDetect";

    /** 图片同步检测操作名 */
    public static final String OPERATION_IMAGE_DETECT = "ImageDetect";

    /** 图片异步检测操作名（提交接口） */
    public static final String OPERATION_IMAGE_ASYNC_SUBMIT = "ImageAsyncSubmit";

    /** 图片异步结果查询操作名 */
    public static final String OPERATION_IMAGE_ASYNC_QUERY = "ImageAsyncQuery";

    /** 文本检测路径 */
    public static final String TEXT_PATH = "/text/v4";

    /** 图片检测路径 */
    public static final String IMAGE_PATH = "/image/v4";

    /** 图片异步提交路径（与同步共用 api-img-{region} 集群） */
    public static final String IMAGE_ASYNC_PATH = "/v4/saas/async/img";

    /** 图片异步主动查询路径 */
    public static final String IMAGE_QUERY_PATH = "/v4/image/query";

    /** 文本集群 host 前缀，完整地址形如 api-text-{region}.fengkongcloud.com */
    public static final String TEXT_HOST_PREFIX = "api-text-";

    /** 图片集群 host 前缀，完整地址形如 api-img-{region}.fengkongcloud.com */
    public static final String IMAGE_HOST_PREFIX = "api-img-";

    /** 集群 host 后缀 */
    public static final String HOST_SUFFIX = ".fengkongcloud.com";

    /** 默认集群：上海（官方文档请求地址表，2026-11-06 调研） */
    public static final String DEFAULT_REGION = "sh";

    /** 默认文本检测风险类型：常规风险检测（含涉政/暴恐/违禁/色情/辱骂/广告/隐私/广告法/无意义） */
    public static final String DEFAULT_TEXT_TYPE = "TEXTRISK";

    /** 默认图片检测风险类型：涉政+色情+暴恐违禁+二维码+广告+图片文字违规 */
    public static final String DEFAULT_IMAGE_TYPE = "POLITY_EROTIC_VIOLENT_QRCODE_ADVERT_IMGTEXTRISK";

    /** extra 键：文本检测风险类型覆盖 */
    public static final String EXTRA_TEXT_TYPE = "text-type";

    /** extra 键：图片检测风险类型覆盖 */
    public static final String EXTRA_IMAGE_TYPE = "image-type";

    /** extra 键：文本接口地址整体覆盖 */
    public static final String EXTRA_TEXT_BASE_URL = "text-base-url";

    /** extra 键：图片接口地址整体覆盖 */
    public static final String EXTRA_IMAGE_BASE_URL = "image-base-url";

    /** 视频文件检测操作名（提交接口） */
    public static final String OPERATION_VIDEO_SUBMIT = "VideoSubmit";

    /** 视频文件检测结果查询操作名 */
    public static final String OPERATION_VIDEO_QUERY = "VideoQuery";

    /** 视频文件提交路径（api-video-{region} 集群，官方文档 2026-11-11 调研） */
    public static final String VIDEO_PATH = "/video/v4";

    /** 视频文件结果主动查询路径 */
    public static final String VIDEO_QUERY_PATH = "/video/query/v4";

    /** 视频集群 host 前缀，完整地址形如 api-video-{region}.fengkongcloud.com */
    public static final String VIDEO_HOST_PREFIX = "api-video-";

    /** 视频处理中返回码（1100 成功；查询专属中间态） */
    public static final int CODE_VIDEO_PROCESSING = 1101;

    /** 默认视频画面识别类型：与图片检测缺省一致（涉政+色情+暴恐违禁+二维码+广告+图片文字违规） */
    public static final String DEFAULT_VIDEO_IMG_TYPE = DEFAULT_IMAGE_TYPE;

    /** 默认视频音频识别类型：涉政+色情+广告+违禁+暴恐+辱骂+广告法（官方 audioType 可选值组合） */
    public static final String DEFAULT_VIDEO_AUDIO_TYPE = "POLITY_EROTIC_ADVERT_BAN_VIOLENT_DIRTY_ADLAW";

    /** 默认截帧方式：0 按 detectFrequency 固定时间间隔截取（官方必传字段） */
    public static final int DEFAULT_SCREENSHOT_TYPE = 0;

    /** extra 键：视频画面识别类型覆盖 */
    public static final String EXTRA_VIDEO_IMG_TYPE = "video-img-type";

    /** extra 键：视频音频识别类型覆盖 */
    public static final String EXTRA_VIDEO_AUDIO_TYPE = "video-audio-type";

    /** extra 键：视频接口地址整体覆盖 */
    public static final String EXTRA_VIDEO_BASE_URL = "video-base-url";

    /** extra 键：截帧方式（0 固定间隔 / 1 高级 / 2 固定张数 / 3 关键帧，官方必传字段） */
    public static final String EXTRA_SCREENSHOT_TYPE = "screenshot-type";

    /** extra 键：连接超时（毫秒） */
    public static final String EXTRA_CONNECT_TIMEOUT_MS = "connect-timeout-ms";

    /** extra 键：读取超时（毫秒） */
    public static final String EXTRA_READ_TIMEOUT_MS = "read-timeout-ms";

    private ShumeiProviderConstants() {
    }
}
