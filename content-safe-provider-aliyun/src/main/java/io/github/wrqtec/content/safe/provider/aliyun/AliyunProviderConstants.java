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
