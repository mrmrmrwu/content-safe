package io.github.wrqtec.content.safe.provider.baidu;

/**
 * 百度内容审核常量
 *
 * @author L.David
 */
public final class BaiduProviderConstants {

    /** 供应商标识 */
    public static final String PROVIDER = "baidu";

    /** 产品线：内容审核平台 */
    public static final String PRODUCT = "content-censor";

    /** 接口版本 */
    public static final String API_VERSION = "v2";

    /** 文本审核操作名 */
    public static final String OPERATION_TEXT_CENSOR = "TextCensor";

    /** 图像审核操作名 */
    public static final String OPERATION_IMAGE_CENSOR = "ImageCensor";

    /** API 基础地址 */
    public static final String BASE_URL = "https://aip.baidubce.com";

    /** access_token 获取路径 */
    public static final String TOKEN_PATH = "/oauth/2.0/token";

    /** 文本审核路径 */
    public static final String TEXT_CENSOR_PATH = "/rest/2.0/solution/v1/text_censor/v2/user_defined";

    /** 图像审核路径 */
    public static final String IMAGE_CENSOR_PATH = "/rest/2.0/solution/v1/img_censor/v2/user_defined";

    /**
     * 长视频审核操作名（异步提交 + 拉取）
     *
     * <p>来源：视频技术文档-请求说明 Format=URL（jksy7j3jv）与拉取结果说明（Pksy7jxr2），
     * 查阅日期 2026-11-11
     */
    public static final String OPERATION_VIDEO_CENSOR = "VideoCensor";

    /** 长视频审核接口版本（solution v1） */
    public static final String API_VERSION_VIDEO = "v1";

    /** 长视频审核提交路径（异步任务） */
    public static final String VIDEO_SUBMIT_PATH = "/rest/2.0/solution/v1/video_censor/v1/video/submit";

    /** 长视频审核结果拉取路径 */
    public static final String VIDEO_PULL_PATH = "/rest/2.0/solution/v1/video_censor/v1/video/pull";

    /** 长视频提交/拉取成功返回码（ret） */
    public static final String CODE_VIDEO_SUCCESS = "0";

    /**
     * 全部结论类型（1 合规 2 违规 3 疑似 4 审核失败），拉取与回调推送（提交参数
     * subEvents）共用。官方默认 2,3（仅违规与疑似）——若不显式携带 1,4，
     * 合规任务拉取不到结论，会表现为任务永远"处理中"；回调场景同理，PASS
     * 任务永远等不到回调推送
     */
    public static final String SUB_EVENTS_ALL = "1,2,3,4";

    /** extra 键：视频审核结果推送地址 noticeUrl（配置后启用回调能力位） */
    public static final String EXTRA_NOTICE_URL = "notice-url";

    /** extra 键：视频审核推送结论范围 subEvents（官方默认 2,3 仅推送违规与疑似） */
    public static final String EXTRA_SUB_EVENTS = "sub-events";

    private BaiduProviderConstants() {
    }
}
