package io.github.wrqtec.content.safe.provider.aliyun;

/**
 * 阿里云 provider 常量。集中管理，杜绝散落魔法值
 *
 * @author L.David
 */
public final class AliyunProviderConstants {

    /** 供应商 key（yml 配置键） */
    public static final String PROVIDER = "aliyun";

    /** 产品线：内容安全 2.0（green-cip） */
    public static final String PRODUCT = "green-cip";

    /** API 版本（ROA 风格 endpoint 版本标识） */
    public static final String API_VERSION = "2022-03-02";

    /** 文本同步检测操作名 */
    public static final String OPERATION_TEXT_MODERATION = "TextModeration";

    /** 图片同步检测操作名 */
    public static final String OPERATION_IMAGE_MODERATION = "ImageModeration";

    /** 图片异步检测提交操作名（来源：help.aliyun.com/doc-detail/2638108，查阅 2026-11-07） */
    public static final String OPERATION_IMAGE_ASYNC_MODERATION = "ImageAsyncModeration";

    /** 视频文件审核增强版提交操作名（来源：help.aliyun.com/zh/document_detail/2505810，查阅 2026-11-11） */
    public static final String OPERATION_VIDEO_MODERATION = "VideoModeration";

    /** 视频文件审核增强版结果查询操作名（来源同上） */
    public static final String OPERATION_VIDEO_MODERATION_RESULT = "VideoModerationResult";

    /** 视频文件审核增强版 Service 固定值（官方默认，来源同上） */
    public static final String SERVICE_VIDEO_DETECTION = "videoDetection";

    /** extra 配置键：异步检测回调地址（配置后提交任务携带 callback，启用回调推送） */
    public static final String EXTRA_CALLBACK_URL = "callback-url";

    /** extra 配置键：回调验签种子（提交时传给阿里云 seed，回调验签 checksum 使用同一值） */
    public static final String EXTRA_CALLBACK_SEED = "callback-seed";

    /** extra 配置键：阿里云主账号 UID（回调 checksum 参与 SHA256 计算，非 RAM 用户 UID） */
    public static final String EXTRA_CALLBACK_UID = "callback-uid";

    /** AIGC 检测 service 码（来源：help.aliyun.com/doc-detail/2642717，查阅 2026-11-06） */
    public static final String SERVICE_AIGC_DETECTOR = "aigcDetector";

    /** AIGC 检测增强版 service 码（来源同上） */
    public static final String SERVICE_AIGC_DETECTOR_FULL = "aigcDetectorFull";

    /**
     * 判断 service 是否为 AIGC 检测类 service 码
     *
     * @param service TextModeration/ImageModeration 的 Service 参数
     * @return true 表示该 service 为 AIGC 检测
     */
    public static boolean isAigcService(String service) {
        return SERVICE_AIGC_DETECTOR.equals(service) || SERVICE_AIGC_DETECTOR_FULL.equals(service);
    }

    private AliyunProviderConstants() {
    }
}
