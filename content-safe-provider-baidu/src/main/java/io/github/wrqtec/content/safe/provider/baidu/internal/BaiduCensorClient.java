package io.github.wrqtec.content.safe.provider.baidu.internal;

/**
 * 百度内容审核客户端（可测试接缝：生产实现走 HTTP，测试可替换）
 *
 * @author L.David
 */
public interface BaiduCensorClient {

    /**
     * 文本审核
     *
     * @param text 待审文本（≤20000 字节 UTF-8）
     * @param strategyId 策略中心 strategyId，null 或空白走默认策略
     */
    BaiduCensorResponse textCensor(String text, String strategyId);

    /**
     * 图像审核（base64 内容）
     *
     * @param base64 base64 编码的图片内容（≤4MB）
     * @param strategyId 策略中心 strategyId，null 或空白走默认策略
     */
    BaiduCensorResponse imageCensorByBase64(String base64, String strategyId);

    /**
     * 图像审核（公网可访问 URL）
     *
     * @param imgUrl 图片 URL
     * @param strategyId 策略中心 strategyId，null 或空白走默认策略
     */
    BaiduCensorResponse imageCensorByUrl(String imgUrl, String strategyId);
}
