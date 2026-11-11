package io.github.wrqtec.content.safe.provider.shumei.internal;

import java.util.List;
import java.util.Map;

/**
 * 数美检测客户端（可测试接缝：生产实现走 HTTP，测试可替换）
 *
 * <p>accessKey/appId 只在实现内部装配进请求体，不出现在接口签名；
 * 数美不存在独立 secret，凭证仅 accessKey。
 *
 * @author L.David
 */
public interface ShumeiClientInvoker {

    /**
     * 文本同步检测（text/v4）
     *
     * @param eventId 与数美约定的事件标识
     * @param type 风险类型（如 TEXTRISK 或下划线组合）
     * @param data data 对象内容（text/tokenId/dataId/ip 等，由调用方装配）
     */
    ShumeiSyncResponse textDetect(String eventId, String type, Map<String, Object> data);

    /**
     * 图片同步检测（image/v4）
     *
     * @param eventId 与数美约定的事件标识
     * @param type 风险类型（如 POLITY_EROTIC 或下划线组合）
     * @param data data 对象内容（img/tokenId/dataId/ip 等，由调用方装配）
     */
    ShumeiSyncResponse imageDetect(String eventId, String type, Map<String, Object> data);

    /**
     * 图片异步检测提交（/v4/saas/async/img，请求体与同步 image/v4 同构）
     *
     * @param eventId 与数美约定的事件标识
     * @param type 风险类型
     * @param data data 对象内容（img/tokenId/dataId/ip 等，由调用方装配）
     * @return 响应 {@code requestId} 即任务引用
     */
    ShumeiSyncResponse imageAsyncSubmit(String eventId, String type, Map<String, Object> data);

    /**
     * 图片异步结果主动查询（/v4/image/query，按 requestIds 查询）
     *
     * @param requestIds 异步提交返回的任务引用列表
     * @return 响应 contents 与同步 image/v4 响应同构（按传入顺序对应）
     */
    ShumeiAsyncQueryResponse imageAsyncQuery(List<String> requestIds);

    /**
     * 视频文件检测提交（/video/v4，官方建议超时 7s）
     *
     * <p>提交响应仅回执 {@code code/message/requestId/btId}，无任务查询句柄——
     * 任务关联键即请求体 {@code data.btId}（调用方装配，本接口原样回显）。
     *
     * @param eventId 与数美约定的事件标识
     * @param imgType 画面识别监管类型（下划线组合，可空）
     * @param audioType 音频识别监管类型（下划线组合，可空）
     * @param data data 对象内容（btId/tokenId/url/screenshotType 等，由调用方装配）
     * @return 提交回执（btId 为回显值）
     */
    ShumeiVideoQueryResponse videoSubmit(String eventId, String imgType, String audioType,
                                         Map<String, Object> data);

    /**
     * 视频文件检测结果主动查询（/video/query/v4，官方建议 30s 间隔、近三天结果）
     *
     * <p>查询响应 1101=处理中为合法中间态，本方法不按成功码断言，由操作层分流。
     *
     * @param btId 提交时的客户侧请求唯一标识（任务关联键）
     * @return 查询响应（含 riskLevel/auxInfo 或处理中标记）
     */
    ShumeiVideoQueryResponse videoQuery(String btId);
}
