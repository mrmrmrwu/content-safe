package io.github.wrqtec.content.safe.api.constant;

/**
 * 内置常用业务场景常量，业务方也可以只通过 yml 注册自定义场景，无需新增常量
 *
 * @author L.David
 */
public final class Scenes {

    /** 昵称 */
    public static final String NICKNAME = "nickname";

    /** 头像 */
    public static final String AVATAR = "avatar";

    /** 评论 */
    public static final String COMMENT = "comment";

    /** 帖子正文 */
    public static final String POST = "post";

    /** 商品图文 */
    public static final String PRODUCT_IMAGE = "product_image";

    /** IM 消息 */
    public static final String IM_MESSAGE = "im_message";

    /** 短视频（异步审核） */
    public static final String SHORT_VIDEO = "short_video";

    private Scenes() {
    }
}
