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

    private BaiduProviderConstants() {
    }
}
