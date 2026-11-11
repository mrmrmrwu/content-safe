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

    /**
     * 长视频审核提交（异步任务）。任务失败重试由业务侧按幂等键控制：
     * 同一 appId+extId 重复提交返回 ret=282005
     *
     * @param url 公网可访问视频 URL（≤3h/≤2G 建议值）
     * @param extId 用户侧唯一标识（appId+extId 组合幂等键；对应业务侧 dataId）
     * @param strategyId 策略中心 strategyId，null 或空白走默认策略
     * @param noticeUrl 结果推送地址，null 不推送（仅轮询拉取）
     * @param subEvents 推送结论范围，null 走官方默认（2,3 仅违规与疑似）
     */
    BaiduVideoSubmitResponse videoSubmit(String url, String extId, String strategyId,
                                         String noticeUrl, String subEvents);

    /**
     * 长视频审核结果拉取。固定携带 subEvents=1,2,3,4（官方默认 2,3 会漏掉合规结论，
     * 导致已完成任务永远查不到终态）
     *
     * @param taskId 供应商侧任务 ID
     */
    BaiduVideoPullResponse videoPull(String taskId);
}
