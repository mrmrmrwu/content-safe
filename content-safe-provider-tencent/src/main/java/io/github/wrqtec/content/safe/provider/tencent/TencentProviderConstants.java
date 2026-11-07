package io.github.wrqtec.content.safe.provider.tencent;

/**
 * 腾讯云内容安全提供者常量
 *
 * @author L.David
 */
public final class TencentProviderConstants {

    /** 提供者标识 */
    public static final String PROVIDER = "tencent";

    /** 产品标识（文本 TMS / 图片 IMS 共用一次声明，API 版本各异） */
    public static final String PRODUCT = "tencentcloud-cms";

    /** API 版本（TMS/IMS 均 2020-12-29） */
    public static final String API_VERSION = "2020-12-29";

    /** 文本同步审核操作（TMS） */
    public static final String OPERATION_TEXT_MODERATION = "TextModeration";

    /** 图片同步审核操作（IMS） */
    public static final String OPERATION_IMAGE_MODERATION = "ImageModeration";

    /** IMS 审核类型：默认（综合识别） */
    public static final String IMAGE_TYPE_IMAGE = "IMAGE";

    /** IMS 审核类型：AIGC 内容识别 */
    public static final String IMAGE_TYPE_AIGC = "IMAGE_AIGC";

    /** 图片异步审核任务创建操作（IMS，结果经回调推送） */
    public static final String OPERATION_IMAGE_ASYNC_CREATE = "CreateImageModerationAsyncTask";

    /** extra 配置键：异步任务回调地址（官方 CallbackUrl，必填） */
    public static final String EXTRA_CALLBACK_URL = "callback-url";

    /** TMS 审核类型：AIGC 生成内容识别（Type=TEXT_AIGC，与内容检测同一接口） */
    public static final String TEXT_TYPE_AIGC = "TEXT_AIGC";

    private TencentProviderConstants() {
    }
}
