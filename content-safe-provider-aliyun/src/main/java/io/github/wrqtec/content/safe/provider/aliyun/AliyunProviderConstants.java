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

    private AliyunProviderConstants() {
    }
}
